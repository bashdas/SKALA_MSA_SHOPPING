package com.skala.orderservice.order.service;

import com.skala.orderservice.client.user.UserServiceClient;
import com.skala.orderservice.client.user.dto.CustomerStatus;
import com.skala.orderservice.client.user.dto.InternalCustomerResponse;
import com.skala.orderservice.client.user.dto.PointOperationResponse;
import com.skala.orderservice.client.user.dto.PointOperationType;
import com.skala.orderservice.client.user.exception.InsufficientFundsException;
import com.skala.orderservice.order.domain.Order;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OrderPlacementIntegrationTest {

	@Autowired OrderPlacementService placementService;
	@Autowired OrderRepository orderRepository;
	@Autowired ProductRepository productRepository;
	@MockitoBean UserServiceClient userServiceClient;

	@BeforeEach
	void setUp() {
		orderRepository.deleteAll();
		productRepository.deleteAll();
		when(userServiceClient.getCustomer(1L))
				.thenReturn(new InternalCustomerResponse(1L, CustomerStatus.ACTIVE, 1_000_000));
	}

	@Test
	void persistsOrderAndDeductsStockAndExactPoints() {
		Product product = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(10_000), 10));
		when(userServiceClient.deductPoints(org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.eq(20_000L), anyString()))
				.thenReturn(pointResponse(20_000));

		OrderCreationResult result = placementService.placeOrder(request(product.getId(), 2));

		assertThat(result.created()).isTrue();
		assertThat(orderRepository.count()).isEqualTo(1);
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
		verify(userServiceClient).deductPoints(
				org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(20_000L), anyString());
	}

	@Test
	void rollsBackOrderAndStockWhenPointsAreInsufficient() {
		Product product = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(10_000), 10));
		when(userServiceClient.deductPoints(
				org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(20_000L), anyString()))
				.thenThrow(new InsufficientFundsException());

		assertThatThrownBy(() -> placementService.placeOrder(request(product.getId(), 2)))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(orderRepository.count()).isZero();
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
	}

	@Test
	void deductsOnlyTwentyThousandWhenExistingOrderGrowsFromThirtyToFiftyThousand() {
		Product product = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(10_000), 10));
		when(userServiceClient.deductPoints(
				org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(30_000L), anyString()))
				.thenReturn(pointResponse(30_000));
		placementService.placeOrder(request(product.getId(), 3));

		when(userServiceClient.deductPoints(
				org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(20_000L), anyString()))
				.thenReturn(pointResponse(20_000));
		OrderCreationResult second = placementService.placeOrder(request(product.getId(), 2));

		Order order = orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(1L).getFirst();
		assertThat(second.created()).isFalse();
		assertThat(order.getTotalAmount()).isEqualByComparingTo("50000");
		verify(userServiceClient).deductPoints(
				org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(20_000L), anyString());
	}

	private CreateOrderRequest request(Long productId, int quantity) {
		return new CreateOrderRequest(1L, List.of(new CreateOrderItemRequest(productId, quantity)));
	}

	private PointOperationResponse pointResponse(long amount) {
		return new PointOperationResponse(1L, "request", PointOperationType.DEDUCT, amount, 1_000_000 - amount);
	}
}
