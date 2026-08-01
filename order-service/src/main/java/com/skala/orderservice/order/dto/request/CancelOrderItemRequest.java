package com.skala.orderservice.order.dto.request;

import jakarta.validation.constraints.Positive;

public record CancelOrderItemRequest(@Positive int quantity) {
}
