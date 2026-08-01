package com.skala.orderservice.order.service;

import com.skala.orderservice.order.dto.response.OrderResponse;

import java.math.BigDecimal;

public record OrderItemCancellationResult(
		Long customerId,
		BigDecimal refundAmount,
		OrderResponse response
) {
}
