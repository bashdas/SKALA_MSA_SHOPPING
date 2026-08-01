package com.skala.orderservice.order.service;

import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.domain.exception.CancelledOrderException;
import com.skala.orderservice.order.domain.exception.ExcessiveCancelQuantityException;
import com.skala.orderservice.order.domain.exception.OrderAlreadyCancelledException;
import com.skala.orderservice.order.domain.exception.OrderItemNotFoundException;
import com.skala.orderservice.order.domain.exception.OrderNotFoundException;
import com.skala.orderservice.order.dto.request.CreateOrderItemRequest;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import com.skala.orderservice.order.dto.response.OrderResponse;
import com.skala.orderservice.order.repository.OrderRepository;
import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.domain.exception.DiscontinuedProductException;
import com.skala.orderservice.product.domain.exception.InsufficientStockException;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import com.skala.orderservice.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock OrderRepository orderRepository;
	@Mock ProductRepository productRepository;
	private OrderService orderService;

	@BeforeEach
	void setUp() {
		orderService = new OrderService(orderRepository, productRepository);
		lenient().when(orderRepository.save(any(Order.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createsNewCustomerOrder() {
		Product product = product(1L, "키보드", 1_000, 10);
		stubNewOrder(1L, product);

		OrderCreationResult result = orderService.createOrAddOrder(request(1L, item(1L, 2)));

		assertThat(result.response().customerId()).isEqualTo(1L);
		assertThat(result.response().items()).hasSize(1);
	}

	@Test
	void reportsNewOrderCreation() {
		Product product = product(1L, "키보드", 1_000, 10);
		stubNewOrder(1L, product);

		assertThat(orderService.createOrAddOrder(request(1L, item(1L, 2))).created()).isTrue();
	}

	@Test
	void deductsProductStockWhenCreatingOrder() {
		Product product = product(1L, "키보드", 1_000, 10);
		stubNewOrder(1L, product);

		orderService.createOrAddOrder(request(1L, item(1L, 2)));

		assertThat(product.getStockQuantity()).isEqualTo(8);
	}

	@Test
	void addsItemToExistingCreatedOrder() {
		Order order = Order.create(1L);
		Product product = product(1L, "키보드", 1_000, 10);
		stubExistingOrder(1L, order, product);

		OrderCreationResult result = orderService.createOrAddOrder(request(1L, item(1L, 2)));

		assertThat(result.response().items()).hasSize(1);
	}

	@Test
	void reportsExistingOrderAccumulation() {
		Order order = Order.create(1L);
		Product product = product(1L, "키보드", 1_000, 10);
		stubExistingOrder(1L, order, product);

		assertThat(orderService.createOrAddOrder(request(1L, item(1L, 2))).created()).isFalse();
	}

	@Test
	void accumulatesSameProductWithoutAddingOrderItem() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		Product product = product(1L, "키보드", 1_000, 10);
		stubExistingOrder(1L, order, product);

		OrderResponse response = orderService.createOrAddOrder(request(1L, item(1L, 3))).response();

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().quantity()).isEqualTo(5);
	}

	@Test
	void preservesOriginalSnapshotWhenSameProductIsAdded() {
		Order order = order(1L, 1L, "기존 키보드", 1_000, 2);
		Product changedProduct = product(1L, "새 키보드", 2_000, 10);
		stubExistingOrder(1L, order, changedProduct);

		OrderResponse response = orderService.createOrAddOrder(request(1L, item(1L, 1))).response();

		assertThat(response.items().getFirst().productName()).isEqualTo("기존 키보드");
		assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("1000");
	}

	@Test
	void addsDifferentProductToExistingOrder() {
		Order order = order(1L, 1L, "키보드", 1_000, 1);
		Product mouse = product(2L, "마우스", 500, 10);
		stubExistingOrder(1L, order, mouse);

		OrderResponse response = orderService.createOrAddOrder(request(1L, item(2L, 1))).response();

		assertThat(response.items()).hasSize(2);
	}

	@Test
	void createsSeparateOrderForDifferentCustomer() {
		Product product = product(1L, "키보드", 1_000, 10);
		stubNewOrder(2L, product);

		OrderCreationResult result = orderService.createOrAddOrder(request(2L, item(1L, 1)));

		assertThat(result.created()).isTrue();
		assertThat(result.response().customerId()).isEqualTo(2L);
		verify(orderRepository).findFirstCreatedOrderByCustomerIdForUpdate(2L);
	}

	@Test
	void aggregatesDuplicateProductIdsWithinRequest() {
		Product product = product(1L, "키보드", 1_000, 10);
		stubNewOrder(1L, product);

		OrderResponse response = orderService.createOrAddOrder(
				request(1L, item(1L, 2), item(1L, 3))).response();

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().quantity()).isEqualTo(5);
		assertThat(product.getStockQuantity()).isEqualTo(5);
	}

	@Test
	void failsWhenProductDoesNotExist() {
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.createOrAddOrder(request(1L, item(1L, 1))))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void failsWhenProductIsDiscontinued() {
		Product product = product(1L, "키보드", 1_000, 10);
		product.discontinue();
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> orderService.createOrAddOrder(request(1L, item(1L, 1))))
				.isInstanceOf(DiscontinuedProductException.class);
	}

	@Test
	void failsWhenStockIsInsufficient() {
		Product product = product(1L, "키보드", 1_000, 1);
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> orderService.createOrAddOrder(request(1L, item(1L, 2))))
				.isInstanceOf(InsufficientStockException.class);
	}

	@Test
	void locksMultipleProductsInProductIdOrder() {
		Product first = product(1L, "키보드", 1_000, 10);
		Product second = product(2L, "마우스", 500, 10);
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
		when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
		when(orderRepository.findFirstCreatedOrderByCustomerIdForUpdate(1L)).thenReturn(Optional.empty());

		orderService.createOrAddOrder(request(1L, item(2L, 1), item(1L, 1)));

		InOrder inOrder = inOrder(productRepository);
		inOrder.verify(productRepository).findByIdForUpdate(1L);
		inOrder.verify(productRepository).findByIdForUpdate(2L);
	}

	@Test
	void getsOrder() {
		Order order = order(1L, 1L, "키보드", 1_000, 1);
		when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

		assertThat(orderService.getOrder(1L).items()).hasSize(1);
	}

	@Test
	void failsToGetMissingOrder() {
		when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.getOrder(1L)).isInstanceOf(OrderNotFoundException.class);
	}

	@Test
	void getsCustomerOrders() {
		when(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(1L))
				.thenReturn(List.of(Order.create(1L), Order.create(1L)));

		assertThat(orderService.getOrdersByCustomer(1L)).hasSize(2);
	}

	@Test
	void returnsEmptyListForCustomerWithoutOrders() {
		when(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

		assertThat(orderService.getOrdersByCustomer(1L)).isEmpty();
	}

	@Test
	void partiallyCancelsOrderItem() {
		Order order = order(1L, 1L, "키보드", 1_000, 3);
		Product product = product(1L, "키보드", 1_000, 7);
		stubCancellation(order, product);

		OrderResponse response = orderService.cancelOrderItem(1L, 1L, 1);

		assertThat(response.items().getFirst().quantity()).isEqualTo(2);
	}

	@Test
	void restoresStockAfterPartialCancellation() {
		Order order = order(1L, 1L, "키보드", 1_000, 3);
		Product product = product(1L, "키보드", 1_000, 7);
		stubCancellation(order, product);

		orderService.cancelOrderItem(1L, 1L, 1);

		assertThat(product.getStockQuantity()).isEqualTo(8);
	}

	@Test
	void removesFullyCancelledOrderItem() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		order.addItem(2L, "마우스", BigDecimal.valueOf(500), 1);
		Product product = product(1L, "키보드", 1_000, 8);
		stubCancellation(order, product);

		OrderResponse response = orderService.cancelOrderItem(1L, 1L, 2);

		assertThat(response.items()).hasSize(1);
	}

	@Test
	void cancelsOrderWhenLastItemIsCancelled() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		Product product = product(1L, "키보드", 1_000, 8);
		stubCancellation(order, product);

		OrderResponse response = orderService.cancelOrderItem(1L, 1L, 2);

		assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void failsToCancelMissingOrderItem() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

		assertThatThrownBy(() -> orderService.cancelOrderItem(1L, 2L, 1))
				.isInstanceOf(OrderItemNotFoundException.class);
	}

	@Test
	void failsToCancelExcessiveQuantity() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		Product product = product(1L, "키보드", 1_000, 8);
		stubCancellation(order, product);

		assertThatThrownBy(() -> orderService.cancelOrderItem(1L, 1L, 3))
				.isInstanceOf(ExcessiveCancelQuantityException.class);
	}

	@Test
	void cancelsEntireOrder() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		Product product = product(1L, "키보드", 1_000, 8);
		stubCancellation(order, product);

		orderService.cancelOrder(1L);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void restoresEveryProductWhenCancellingEntireOrder() {
		Order order = order(1L, 1L, "키보드", 1_000, 2);
		order.addItem(2L, "마우스", BigDecimal.valueOf(500), 1);
		Product keyboard = product(1L, "키보드", 1_000, 8);
		Product mouse = product(2L, "마우스", 500, 9);
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(keyboard));
		when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(mouse));

		orderService.cancelOrder(1L);

		assertThat(keyboard.getStockQuantity()).isEqualTo(10);
		assertThat(mouse.getStockQuantity()).isEqualTo(10);
	}

	@Test
	void rejectsDuplicateEntireCancellation() {
		Order order = Order.create(1L);
		order.cancel();
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

		assertThatThrownBy(() -> orderService.cancelOrder(1L))
				.isInstanceOf(OrderAlreadyCancelledException.class);
	}

	@Test
	void rejectsPartialCancellationOfCancelledOrder() {
		Order order = Order.create(1L);
		order.cancel();
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

		assertThatThrownBy(() -> orderService.cancelOrderItem(1L, 1L, 1))
				.isInstanceOf(CancelledOrderException.class);
	}

	private void stubNewOrder(Long customerId, Product product) {
		when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
		when(orderRepository.findFirstCreatedOrderByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());
	}

	private void stubExistingOrder(Long customerId, Order order, Product product) {
		when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
		when(orderRepository.findFirstCreatedOrderByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(order));
	}

	private void stubCancellation(Order order, Product product) {
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
		when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
	}

	private Product product(Long id, String name, long price, int stock) {
		Product product = Product.create(name, BigDecimal.valueOf(price), stock);
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}

	private Order order(Long customerId, Long productId, String name, long price, int quantity) {
		Order order = Order.create(customerId);
		order.addItem(productId, name, BigDecimal.valueOf(price), quantity);
		return order;
	}

	private CreateOrderRequest request(Long customerId, CreateOrderItemRequest... items) {
		return new CreateOrderRequest(customerId, List.of(items));
	}

	private CreateOrderItemRequest item(Long productId, int quantity) {
		return new CreateOrderItemRequest(productId, quantity);
	}
}
