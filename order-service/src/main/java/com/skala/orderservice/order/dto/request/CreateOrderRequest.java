package com.skala.orderservice.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
		@NotEmpty List<@Valid CreateOrderItemRequest> items
) {
	/**
	 * Source-compatible constructor for internal tests written against the pre-JWT DTO.
	 * The supplied customer id is deliberately discarded and is not part of the JSON contract.
	 */
	@Deprecated(forRemoval = true)
	public CreateOrderRequest(Long ignoredCustomerId, List<CreateOrderItemRequest> items) {
		this(items);
	}
}
