package com.skala.orderservice.order.dto.response;

import com.skala.orderservice.order.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
		Long productId,
		String productName,
		BigDecimal unitPrice,
		int quantity,
		BigDecimal subtotal
) {

	public static OrderItemResponse from(OrderItem orderItem) {
		return new OrderItemResponse(
				orderItem.getProductId(),
				orderItem.getProductName(),
				orderItem.getUnitPrice(),
				orderItem.getQuantity(),
				orderItem.getSubtotal()
		);
	}
}
