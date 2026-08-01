package com.skala.orderservice.order.domain.exception;

public class ExcessiveCancelQuantityException extends RuntimeException {

	public ExcessiveCancelQuantityException(String message) {
		super(message);
	}
}
