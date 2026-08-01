package com.skala.orderservice.order.service;

import com.skala.orderservice.client.user.UserServiceClient;
import com.skala.orderservice.client.user.dto.PointOperationResponse;
import com.skala.orderservice.client.user.dto.PointOperationType;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.dto.request.CreateOrderItemRequest;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import com.skala.orderservice.order.repository.OrderRepository;
import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OrderCancellationIntegrationTest {

	@Autowired OrderCancellationService cancellationService;
	@Autowired OrderService orderService;
	@Autowired OrderRepository orderRepository;
	@Autowired ProductRepository productRepository;
	@MockitoBean UserServiceClient userServiceClient;

	@BeforeEach
	void setUp() {
		orderRepository.deleteAll();
		productRepository.deleteAll();
	}

	@Test
	void partiallyCancelsUsingSnapshotPriceAndRestoresStock() {
		Product product = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(1_000), 10));
		OrderCreationResult created = createLocalOrder(product.getId(), 5);
		Product currentProduct = productRepository.findById(product.getId()).orElseThrow();
		currentProduct.updateInfo("가격 변경 키보드", BigDecimal.valueOf(2_000));
		productRepository.saveAndFlush(currentProduct);
		stubRefundSuccess();

		var response = cancellationService.cancelOrderItem(created.response().id(), product.getId(), 2);

		assertThat(response.items().getFirst().quantity()).isEqualTo(3);
		assertThat(response.items().getFirst().subtotal()).isEqualByComparingTo("3000");
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(7);
		verify(userServiceClient).refundPoints(eq(1L), eq(2_000L), anyString());
	}

	@Test
	void entirelyCancelsAndRefundsCurrentTotalAndRestoresEveryProduct() {
		Product first = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(1_000), 10));
		Product second = productRepository.saveAndFlush(
				Product.create("마우스", BigDecimal.valueOf(2_000), 10));
		OrderCreationResult created = orderService.createOrAddOrder(new CreateOrderRequest(1L, List.of(
				new CreateOrderItemRequest(first.getId(), 2),
				new CreateOrderItemRequest(second.getId(), 3))));
		stubRefundSuccess();

		cancellationService.cancelOrder(created.response().id());

		Order order = orderRepository.findByIdWithItems(created.response().id()).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(order.getOrderItems()).isEmpty();
		assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(productRepository.findById(first.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
		assertThat(productRepository.findById(second.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
		verify(userServiceClient).refundPoints(eq(1L), eq(8_000L), anyString());
	}

	@Test
	void rollsBackOrderItemAndProductWhenRefundFails() {
		Product product = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(1_000), 10));
		OrderCreationResult created = createLocalOrder(product.getId(), 5);
		when(userServiceClient.refundPoints(anyLong(), anyLong(), anyString()))
				.thenThrow(new UserServiceUnavailableException());

		assertThatThrownBy(() -> cancellationService.cancelOrderItem(
				created.response().id(), product.getId(), 2))
				.isInstanceOf(UserServiceUnavailableException.class);

		Order order = orderRepository.findByIdWithItems(created.response().id()).orElseThrow();
		assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(5);
		assertThat(order.getTotalAmount()).isEqualByComparingTo("5000");
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
	}

	@Test
	void refundsOnlyRemainingAmountAfterPreviousPartialCancellation() {
		Product product = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(10_000), 10));
		OrderCreationResult created = createLocalOrder(product.getId(), 10);
		stubRefundSuccess();
		cancellationService.cancelOrderItem(created.response().id(), product.getId(), 3);
		clearInvocations(userServiceClient);

		cancellationService.cancelOrder(created.response().id());

		verify(userServiceClient).refundPoints(eq(1L), eq(70_000L), anyString());
	}

	private OrderCreationResult createLocalOrder(Long productId, int quantity) {
		return orderService.createOrAddOrder(new CreateOrderRequest(
				1L, List.of(new CreateOrderItemRequest(productId, quantity))));
	}

	private void stubRefundSuccess() {
		when(userServiceClient.refundPoints(anyLong(), anyLong(), anyString()))
				.thenAnswer(invocation -> new PointOperationResponse(
						invocation.getArgument(0), invocation.getArgument(2), PointOperationType.REFUND,
						invocation.getArgument(1), 100_000));
	}
}
