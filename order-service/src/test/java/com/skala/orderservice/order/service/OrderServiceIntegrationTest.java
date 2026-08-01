package com.skala.orderservice.order.service;

import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.dto.request.CreateOrderItemRequest;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import com.skala.orderservice.order.repository.OrderRepository;
import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.domain.exception.InsufficientStockException;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import com.skala.orderservice.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

	@Autowired OrderService orderService;
	@Autowired OrderRepository orderRepository;
	@Autowired ProductRepository productRepository;

	@BeforeEach
	void cleanDatabase() {
		orderRepository.deleteAll();
		productRepository.deleteAll();
	}

	@Test
	void rollsBackEveryStockDeductionWhenOneProductIsInsufficient() {
		Product productA = productRepository.saveAndFlush(Product.create("상품 A", BigDecimal.valueOf(1_000), 10));
		Product productB = productRepository.saveAndFlush(Product.create("상품 B", BigDecimal.valueOf(2_000), 1));
		CreateOrderRequest request = request(1L,
				new CreateOrderItemRequest(productA.getId(), 2),
				new CreateOrderItemRequest(productB.getId(), 5));

		assertThatThrownBy(() -> orderService.createOrAddOrder(request))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(productRepository.findById(productA.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
		assertThat(productRepository.findById(productB.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
		assertThat(orderRepository.count()).isZero();
	}

	@Test
	void rollsBackRestoredStockAndOrderChangeWhenEntireCancellationFails() {
		Product productA = productRepository.saveAndFlush(Product.create("상품 A", BigDecimal.valueOf(1_000), 10));
		Product productB = productRepository.saveAndFlush(Product.create("상품 B", BigDecimal.valueOf(2_000), 10));
		OrderCreationResult created = orderService.createOrAddOrder(request(1L,
				new CreateOrderItemRequest(productA.getId(), 2),
				new CreateOrderItemRequest(productB.getId(), 1)));
		Long orderId = created.response().id();
		productRepository.deleteById(productB.getId());

		assertThatThrownBy(() -> orderService.cancelOrder(orderId))
				.isInstanceOf(ProductNotFoundException.class);

		assertThat(productRepository.findById(productA.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
		Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(order.getOrderItems()).hasSize(2);
	}

	@Test
	void sequentialOrdersFromSameCustomerAccumulateIntoOneOrderAndItem() {
		Product product = productRepository.saveAndFlush(Product.create("키보드", BigDecimal.valueOf(1_000), 10));

		orderService.createOrAddOrder(request(1L, new CreateOrderItemRequest(product.getId(), 2)));
		OrderCreationResult second = orderService.createOrAddOrder(
				request(1L, new CreateOrderItemRequest(product.getId(), 3)));

		assertThat(second.created()).isFalse();
		assertThat(orderRepository.count()).isEqualTo(1);
		Order order = orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(1L).getFirst();
		assertThat(order.getOrderItems()).hasSize(1);
		assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(5);
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
	}

	private CreateOrderRequest request(Long customerId, CreateOrderItemRequest... items) {
		return new CreateOrderRequest(customerId, List.of(items));
	}
}
