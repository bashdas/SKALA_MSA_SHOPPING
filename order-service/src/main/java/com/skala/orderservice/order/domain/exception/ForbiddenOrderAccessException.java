package com.skala.orderservice.order.domain.exception;

public class ForbiddenOrderAccessException extends RuntimeException {

	public ForbiddenOrderAccessException() {
		super("해당 주문에 접근할 권한이 없습니다.");
	}
}
