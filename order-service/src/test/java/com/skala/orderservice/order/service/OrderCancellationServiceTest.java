package com.skala.orderservice.order.service;

import com.skala.orderservice.client.user.UserServiceClient;
import com.skala.orderservice.client.user.dto.PointOperationResponse;
import com.skala.orderservice.client.user.dto.PointOperationType;
import com.skala.orderservice.client.user.exception.PointRequestConflictException;
import com.skala.orderservice.client.user.exception.UserServiceResponseException;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.domain.exception.OrderCancellationCompensationFailedException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

	private static final String UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";

	@Mock OrderService orderService;
	@Mock UserServiceClient userServiceClient;
	@Mock PlatformTransactionManager transactionManager;
	@Mock TransactionStatus transactionStatus;
	private OrderCancellationService cancellationService;

	@BeforeEach
	void setUp() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		cancellationService = new OrderCancellationService(
				orderService,
				userServiceClient,
				new OrderPointAmountConverter(),
				new OrderCancellationPointRequestIdGenerator(() -> UUID.fromString(UUID_VALUE)),
				transactionManager
		);
	}

	@Test
	void refundsSnapshotAmountForPartialCancellation() {
		stubPartialResult(new BigDecimal("78000"));
		stubRefundSuccess(78_000);

		OrderResponse response = cancellationService.cancelOrderItem(1L, 1L, 2);

		assertThat(response.id()).isEqualTo(1L);
		verify(userServiceClient).refundPoints(1L, 78_000, refundId());
	}

	@Test
	void usesCancellationRefundRequestId() {
		stubPartialResult(BigDecimal.ONE);
		stubRefundSuccess(1);

		cancellationService.cancelOrderItem(1L, 1L, 1);

		verify(userServiceClient).refundPoints(1L, 1, "ORDER-CANCEL-" + UUID_VALUE + "-REFUND");
	}

	@Test
	void refundsCurrentRemainingAmountForEntireCancellation() {
		when(orderService.cancelOrderLocally(1L))
				.thenReturn(new OrderCancellationResult(1L, BigDecimal.valueOf(70_000)));
		stubRefundSuccess(70_000);

		cancellationService.cancelOrder(1L);

		verify(userServiceClient).refundPoints(1L, 70_000, refundId());
	}

	@Test
	void commitsAfterConfirmedRefund() {
		stubPartialResult(BigDecimal.ONE);
		stubRefundSuccess(1);

		cancellationService.cancelOrderItem(1L, 1L, 1);

		verify(transactionManager).commit(transactionStatus);
	}

	@Test
	void rollsBackWhenWithdrawnCustomerRefundFails() {
		assertRefundFailureRollsBack(new WithdrawnCustomerException());
	}

	@Test
	void rollsBackWhenPointRequestConflicts() {
		assertRefundFailureRollsBack(new PointRequestConflictException());
	}

	@Test
	void rollsBackWhenUserServiceIsUnavailable() {
		assertRefundFailureRollsBack(new UserServiceUnavailableException());
	}

	@Test
	void rollsBackOnUnknownUserServiceResponse() {
		assertRefundFailureRollsBack(new UserServiceResponseException());
	}

	@Test
	void reDeductsPointsWhenCommitFailsAfterRefund() {
		RuntimeException commitFailure = new RuntimeException("commit failed");
		stubPartialResult(BigDecimal.valueOf(2_000));
		stubRefundSuccess(2_000);
		doThrow(commitFailure).when(transactionManager).commit(transactionStatus);
		when(userServiceClient.deductPoints(1L, 2_000, reDeductId()))
				.thenReturn(pointResponse(2_000, PointOperationType.DEDUCT));

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(1L, 1L, 1))
				.isSameAs(commitFailure);
		verify(userServiceClient).deductPoints(1L, 2_000, reDeductId());
	}

	@Test
	void reDeductUsesSameUuidAndRefundAmount() {
		stubPartialResult(BigDecimal.valueOf(3_456));
		stubRefundSuccess(3_456);
		doThrow(new RuntimeException("commit failed")).when(transactionManager).commit(transactionStatus);
		when(userServiceClient.deductPoints(1L, 3_456, reDeductId()))
				.thenReturn(pointResponse(3_456, PointOperationType.DEDUCT));

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(1L, 1L, 1));

		verify(userServiceClient).refundPoints(1L, 3_456, refundId());
		verify(userServiceClient).deductPoints(1L, 3_456, reDeductId());
	}

	@Test
	void preservesOriginalCommitFailureAfterSuccessfulReDeduct() {
		RuntimeException commitFailure = new RuntimeException("commit failed");
		stubPartialResult(BigDecimal.ONE);
		stubRefundSuccess(1);
		doThrow(commitFailure).when(transactionManager).commit(transactionStatus);
		when(userServiceClient.deductPoints(1L, 1, reDeductId()))
				.thenReturn(pointResponse(1, PointOperationType.DEDUCT));

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(1L, 1L, 1))
				.isSameAs(commitFailure);
	}

	@Test
	void reportsReDeductCompensationFailure() {
		stubPartialResult(BigDecimal.ONE);
		stubRefundSuccess(1);
		doThrow(new RuntimeException("commit failed")).when(transactionManager).commit(transactionStatus);
		when(userServiceClient.deductPoints(1L, 1, reDeductId()))
				.thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(1L, 1L, 1))
				.isInstanceOf(OrderCancellationCompensationFailedException.class)
				.hasMessage("주문 취소 보상에 실패했습니다.");
	}

	@Test
	void doesNotReDeductWhenRefundResultIsUncertain() {
		stubPartialResult(BigDecimal.ONE);
		when(userServiceClient.refundPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(1L, 1L, 1))
				.isInstanceOf(UserServiceUnavailableException.class);
		verify(userServiceClient, never()).deductPoints(anyLong(), anyLong(), anyString());
	}

	private void assertRefundFailureRollsBack(RuntimeException failure) {
		stubPartialResult(BigDecimal.ONE);
		when(userServiceClient.refundPoints(anyLong(), anyLong(), anyString())).thenThrow(failure);

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(1L, 1L, 1))
				.isSameAs(failure);
		verify(transactionManager).rollback(transactionStatus);
		verify(userServiceClient, never()).deductPoints(anyLong(), anyLong(), anyString());
	}

	private void stubPartialResult(BigDecimal amount) {
		when(orderService.cancelOrderItemLocally(eq(1L), eq(1L), anyInt()))
				.thenReturn(new OrderItemCancellationResult(1L, amount, response()));
	}

	private void stubRefundSuccess(long amount) {
		when(userServiceClient.refundPoints(1L, amount, refundId()))
				.thenReturn(pointResponse(amount, PointOperationType.REFUND));
	}

	private PointOperationResponse pointResponse(long amount, PointOperationType type) {
		return new PointOperationResponse(1L, "request", type, amount, 10_000);
	}

	private OrderResponse response() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
		return new OrderResponse(1L, 1L, OrderStatus.CREATED, BigDecimal.ONE, List.of(), now, now);
	}

	private String refundId() {
		return "ORDER-CANCEL-" + UUID_VALUE + "-REFUND";
	}

	private String reDeductId() {
		return "ORDER-CANCEL-" + UUID_VALUE + "-REDEDUCT";
	}
}
