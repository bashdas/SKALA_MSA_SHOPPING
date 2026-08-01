package com.skala.orderservice.order.service;

import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.exception.ForbiddenOrderAccessException;
import com.skala.orderservice.order.repository.OrderRepository;
import com.skala.orderservice.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderOwnershipTest {

	private OrderRepository orderRepository;
	private ProductRepository productRepository;
	private OrderService orderService;
	private Order order;

	@BeforeEach
	void setUp() {
		orderRepository = mock(OrderRepository.class);
		productRepository = mock(ProductRepository.class);
		orderService = new OrderService(orderRepository, productRepository);
		order = Order.create(1L);
		order.addItem(10L, "키보드", BigDecimal.valueOf(1_000), 2);
	}

	@Test
	void ownerCanReadOrder() {
		when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));
		assertThat(orderService.getOrder(1L, 1L).customerId()).isEqualTo(1L);
	}

	@Test
	void anotherCustomerCannotReadOrder() {
		when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));
		assertThatThrownBy(() -> orderService.getOrder(1L, 2L))
				.isInstanceOf(ForbiddenOrderAccessException.class);
	}

	@Test
	void anotherCustomerCannotPartiallyCancelOrRestoreStock() {
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
		assertThatThrownBy(() -> orderService.cancelOrderItemLocally(1L, 10L, 1, 2L))
				.isInstanceOf(ForbiddenOrderAccessException.class);
		assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(2);
		assertThat(order.getTotalAmount()).isEqualByComparingTo("2000");
		verify(productRepository, never()).findByIdForUpdate(10L);
	}

	@Test
	void anotherCustomerCannotCancelEntireOrderOrRestoreStock() {
		when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
		assertThatThrownBy(() -> orderService.cancelOrderLocally(1L, 2L))
				.isInstanceOf(ForbiddenOrderAccessException.class);
		assertThat(order.getOrderItems()).hasSize(1);
		verify(productRepository, never()).findByIdForUpdate(10L);
	}
}
