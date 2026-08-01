package com.skala.orderservice.order.service;

import com.skala.orderservice.client.user.UserServiceClient;
import com.skala.orderservice.client.user.dto.CustomerStatus;
import com.skala.orderservice.client.user.dto.InternalCustomerResponse;
import com.skala.orderservice.client.user.dto.PointOperationResponse;
import com.skala.orderservice.client.user.dto.PointOperationType;
import com.skala.orderservice.client.user.exception.CustomerNotFoundException;
import com.skala.orderservice.client.user.exception.InsufficientFundsException;
import com.skala.orderservice.client.user.exception.PointRequestConflictException;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.domain.exception.OrderCompensationFailedException;
import com.skala.orderservice.order.dto.request.CreateOrderItemRequest;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import com.skala.orderservice.order.dto.response.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

	private static final String UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";

	@Mock OrderService orderService;
	@Mock UserServiceClient userServiceClient;
	@Mock PlatformTransactionManager transactionManager;
	@Mock TransactionStatus transactionStatus;
	private OrderPlacementService placementService;

	@BeforeEach
	void setUp() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		placementService = new OrderPlacementService(
				orderService,
				userServiceClient,
				new OrderPointAmountConverter(),
				new OrderPointRequestIdGenerator(() -> UUID.fromString(UUID_VALUE)),
				transactionManager
		);
	}

	@Test
	void placesOrderForActiveCustomer() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("2000"), true);
		stubDeductSuccess(2_000);

		OrderCreationResult result = placementService.placeOrder(request());

		assertThat(result.created()).isTrue();
		verify(orderService).createOrAddOrder(any());
	}

	@Test
	void rejectsMissingCustomerBeforeLocalOrderChanges() {
		when(userServiceClient.getCustomer(1L)).thenThrow(new CustomerNotFoundException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(CustomerNotFoundException.class);
		verify(orderService, never()).createOrAddOrder(any());
	}

	@Test
	void rejectsWithdrawnCustomer() {
		when(userServiceClient.getCustomer(1L))
				.thenReturn(new InternalCustomerResponse(1L, CustomerStatus.WITHDRAWN, 10_000));

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(WithdrawnCustomerException.class);
		verify(orderService, never()).createOrAddOrder(any());
	}

	@Test
	void customerLookupFailureDoesNotStartLocalOrderChanges() {
		when(userServiceClient.getCustomer(1L)).thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(UserServiceUnavailableException.class);
		verify(transactionManager, never()).getTransaction(any());
	}

	@Test
	void deductsEntireAmountForNewOrder() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("2000"), true);
		stubDeductSuccess(2_000);

		placementService.placeOrder(request());

		verify(userServiceClient).deductPoints(1L, 2_000, deductId());
	}

	@Test
	void deductsOnlyIncreaseForExistingOrder() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("20000"), false);
		stubDeductSuccess(20_000);

		placementService.placeOrder(request());

		verify(userServiceClient).deductPoints(1L, 20_000, deductId());
	}

	@Test
	void usesDomainCalculatedSnapshotIncreaseForRepeatedProduct() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("3000"), false);
		stubDeductSuccess(3_000);

		placementService.placeOrder(request());

		verify(userServiceClient).deductPoints(1L, 3_000, deductId());
	}

	@Test
	void deductsCurrentPriceIncreaseForNewProduct() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("4200"), false);
		stubDeductSuccess(4_200);

		placementService.placeOrder(request());

		verify(userServiceClient).deductPoints(1L, 4_200, deductId());
	}

	@Test
	void deductsAggregatedIncreaseForDuplicateRequestProducts() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("5000"), true);
		stubDeductSuccess(5_000);

		placementService.placeOrder(request(
				new CreateOrderItemRequest(1L, 2), new CreateOrderItemRequest(1L, 3)));

		verify(userServiceClient).deductPoints(1L, 5_000, deductId());
	}

	@Test
	void usesDeductRequestIdFormat() {
		stubActiveCustomer();
		stubOrderResult(BigDecimal.ONE, true);
		stubDeductSuccess(1);

		placementService.placeOrder(request());

		verify(userServiceClient).deductPoints(1L, 1, "ORDER-" + UUID_VALUE + "-DEDUCT");
	}

	@Test
	void rollsBackLocalTransactionWhenFundsAreInsufficient() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("2000"), true);
		when(userServiceClient.deductPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new InsufficientFundsException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(InsufficientFundsException.class);
		verify(transactionManager).rollback(transactionStatus);
		verify(userServiceClient, never()).refundPoints(anyLong(), anyLong(), anyString());
	}

	@Test
	void rollsBackLocalTransactionWhenUserServiceIsUnavailable() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("2000"), true);
		when(userServiceClient.deductPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(UserServiceUnavailableException.class);
		verify(transactionManager).rollback(transactionStatus);
	}

	@Test
	void rollsBackLocalTransactionOnPointRequestConflict() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("2000"), true);
		when(userServiceClient.deductPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new PointRequestConflictException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(PointRequestConflictException.class);
		verify(transactionManager).rollback(transactionStatus);
	}

	@Test
	void compensatesWhenCommitFailsAfterConfirmedDeduction() {
		RuntimeException commitFailure = new RuntimeException("commit failed");
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("2000"), true);
		stubDeductSuccess(2_000);
		doThrow(commitFailure).when(transactionManager).commit(transactionStatus);

		assertThatThrownBy(() -> placementService.placeOrder(request())).isSameAs(commitFailure);
		verify(userServiceClient).refundPoints(1L, 2_000, refundId());
	}

	@Test
	void refundUsesSameUuidAndActualDeductedAmount() {
		stubActiveCustomer();
		stubOrderResult(new BigDecimal("3456"), true);
		stubDeductSuccess(3_456);
		doThrow(new RuntimeException("commit failed")).when(transactionManager).commit(transactionStatus);

		assertThatThrownBy(() -> placementService.placeOrder(request()));
		verify(userServiceClient).refundPoints(1L, 3_456, "ORDER-" + UUID_VALUE + "-REFUND");
	}

	@Test
	void preservesOriginalFailureAfterSuccessfulRefund() {
		RuntimeException commitFailure = new RuntimeException("commit failed");
		stubActiveCustomer();
		stubOrderResult(BigDecimal.ONE, true);
		stubDeductSuccess(1);
		doThrow(commitFailure).when(transactionManager).commit(transactionStatus);

		assertThatThrownBy(() -> placementService.placeOrder(request())).isSameAs(commitFailure);
	}

	@Test
	void reportsCompensationFailure() {
		stubActiveCustomer();
		stubOrderResult(BigDecimal.ONE, true);
		stubDeductSuccess(1);
		doThrow(new RuntimeException("commit failed")).when(transactionManager).commit(transactionStatus);
		when(userServiceClient.refundPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(OrderCompensationFailedException.class)
				.hasMessage("주문 처리 보상에 실패했습니다.");
	}

	@Test
	void doesNotRefundWhenDeductionResultIsUncertain() {
		stubActiveCustomer();
		stubOrderResult(BigDecimal.ONE, true);
		when(userServiceClient.deductPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> placementService.placeOrder(request()))
				.isInstanceOf(UserServiceUnavailableException.class);
		verify(userServiceClient, never()).refundPoints(anyLong(), anyLong(), anyString());
	}

	private void stubActiveCustomer() {
		when(userServiceClient.getCustomer(1L))
				.thenReturn(new InternalCustomerResponse(1L, CustomerStatus.ACTIVE, 10_000));
	}

	private void stubOrderResult(BigDecimal increase, boolean created) {
		when(orderService.createOrAddOrder(any()))
				.thenReturn(new OrderCreationResult(response(increase), created, increase));
	}

	private void stubDeductSuccess(long amount) {
		when(userServiceClient.deductPoints(1L, amount, deductId()))
				.thenReturn(new PointOperationResponse(
						1L, deductId(), PointOperationType.DEDUCT, amount, 10_000 - amount));
	}

	private OrderResponse response(BigDecimal total) {
		return new OrderResponse(1L, 1L, OrderStatus.CREATED, total, List.of(),
				LocalDateTime.of(2026, 8, 1, 12, 0), LocalDateTime.of(2026, 8, 1, 12, 0));
	}

	private CreateOrderRequest request(CreateOrderItemRequest... items) {
		List<CreateOrderItemRequest> requestItems = items.length == 0
				? List.of(new CreateOrderItemRequest(1L, 2)) : List.of(items);
		return new CreateOrderRequest(1L, requestItems);
	}

	private String deductId() {
		return "ORDER-" + UUID_VALUE + "-DEDUCT";
	}

	private String refundId() {
		return "ORDER-" + UUID_VALUE + "-REFUND";
	}
}
