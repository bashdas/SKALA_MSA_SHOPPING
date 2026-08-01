package com.skala.orderservice.order.domain.exception;

public class CancelledOrderException extends RuntimeException {

	public CancelledOrderException(String message) {
		super(message);
	}
}
