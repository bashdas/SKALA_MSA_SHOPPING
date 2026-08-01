package com.skala.orderservice.product.dto.request;

import jakarta.validation.constraints.Positive;

public record AddProductStockRequest(@Positive int quantity) {
}
