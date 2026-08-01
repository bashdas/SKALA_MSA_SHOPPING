package com.skala.orderservice.order.domain.exception;

public class InvalidCustomerIdException extends RuntimeException {

	public InvalidCustomerIdException(String message) {
		super(message);
	}
}
