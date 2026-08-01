package com.skala.orderservice.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull @DecimalMin("0.0") BigDecimal price,
		@PositiveOrZero int stockQuantity
) {
}
