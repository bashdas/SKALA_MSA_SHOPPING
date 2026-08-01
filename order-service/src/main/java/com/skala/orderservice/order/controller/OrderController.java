package com.skala.orderservice.order.controller;

import com.skala.orderservice.order.dto.request.CancelOrderItemRequest;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import com.skala.orderservice.order.dto.response.OrderResponse;
import com.skala.orderservice.order.service.OrderCreationResult;
import com.skala.orderservice.order.service.OrderCancellationService;
import com.skala.orderservice.order.service.OrderPlacementService;
import com.skala.orderservice.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;
	private final OrderPlacementService orderPlacementService;
	private final OrderCancellationService orderCancellationService;

	public OrderController(
			OrderService orderService,
			OrderPlacementService orderPlacementService,
			OrderCancellationService orderCancellationService) {
		this.orderService = orderService;
		this.orderPlacementService = orderPlacementService;
		this.orderCancellationService = orderCancellationService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> createOrAddOrder(@Valid @RequestBody CreateOrderRequest request) {
		OrderCreationResult result = orderPlacementService.placeOrder(request);
		if (result.created()) {
			return ResponseEntity
					.created(URI.create("/api/orders/" + result.response().id()))
					.body(result.response());
		}
		return ResponseEntity.ok(result.response());
	}

	@GetMapping("/{orderId}")
	public OrderResponse getOrder(@PathVariable Long orderId) {
		return orderService.getOrder(orderId);
	}

	@GetMapping
	public List<OrderResponse> getOrdersByCustomer(@RequestParam @Positive Long customerId) {
		return orderService.getOrdersByCustomer(customerId);
	}

	@PatchMapping("/{orderId}/items/{productId}/cancel")
	public OrderResponse cancelOrderItem(
			@PathVariable Long orderId,
			@PathVariable Long productId,
			@Valid @RequestBody CancelOrderItemRequest request) {
		return orderCancellationService.cancelOrderItem(orderId, productId, request.quantity());
	}

	@PatchMapping("/{orderId}/cancel")
	public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
		orderCancellationService.cancelOrder(orderId);
		return ResponseEntity.noContent().build();
	}
}
