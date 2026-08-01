package com.skala.orderservice.order.service;

import com.skala.orderservice.order.dto.response.OrderResponse;

public record OrderCreationResult(OrderResponse response, boolean created) {
}
