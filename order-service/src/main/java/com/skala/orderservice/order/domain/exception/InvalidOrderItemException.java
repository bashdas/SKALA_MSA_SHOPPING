package com.skala.orderservice.order.domain.exception;

public class InvalidOrderItemException extends RuntimeException {

	public InvalidOrderItemException(String message) {
		super(message);
	}
}
