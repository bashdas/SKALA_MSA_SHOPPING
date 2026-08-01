package com.skala.orderservice.product.dto.response;

import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
		Long id,
		String name,
		BigDecimal price,
		int stockQuantity,
		ProductStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(
				product.getId(),
				product.getName(),
				product.getPrice(),
				product.getStockQuantity(),
				product.getStatus(),
				product.getCreatedAt(),
				product.getUpdatedAt()
		);
	}
}
