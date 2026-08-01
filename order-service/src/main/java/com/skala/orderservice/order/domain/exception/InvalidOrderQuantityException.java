package com.skala.orderservice.order.domain.exception;

public class InvalidOrderQuantityException extends RuntimeException {

	public InvalidOrderQuantityException(String message) {
		super(message);
	}
}
