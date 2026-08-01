package com.skala.orderservice.client.user.dto;

public record PointOperationResponse(
		Long customerId,
		String requestId,
		PointOperationType type,
		long amount,
		long balance
) {
}
