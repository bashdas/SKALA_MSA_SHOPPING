package com.skala.orderservice.client.user.dto;

public record UserServiceErrorResponse(
		String timestamp,
		int status,
		String code,
		String message,
		String path
) {
}
