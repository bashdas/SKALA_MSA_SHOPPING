package com.skala.orderservice.order.service;

import com.skala.orderservice.order.dto.response.OrderResponse;

import java.math.BigDecimal;

public record OrderCreationResult(OrderResponse response, boolean created, BigDecimal increasedAmount) {

	public OrderCreationResult(OrderResponse response, boolean created) {
		this(response, created, response.totalAmount());
	}
}
