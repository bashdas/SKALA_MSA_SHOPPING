package com.skala.orderservice.product.dto.response;

import com.skala.orderservice.product.domain.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
		List<ProductResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {

	public static ProductPageResponse from(Page<Product> products) {
		return new ProductPageResponse(
				products.getContent().stream().map(ProductResponse::from).toList(),
				products.getNumber(),
				products.getSize(),
				products.getTotalElements(),
				products.getTotalPages(),
				products.isFirst(),
				products.isLast()
		);
	}
}
