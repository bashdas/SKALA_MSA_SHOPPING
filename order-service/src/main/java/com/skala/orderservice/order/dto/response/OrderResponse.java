package com.skala.orderservice.order.dto.response;

import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
		Long id,
		Long customerId,
		OrderStatus status,
		BigDecimal totalAmount,
		List<OrderItemResponse> items,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getCustomerId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getOrderItems().stream().map(OrderItemResponse::from).toList(),
				order.getCreatedAt(),
				order.getUpdatedAt()
		);
	}
}
