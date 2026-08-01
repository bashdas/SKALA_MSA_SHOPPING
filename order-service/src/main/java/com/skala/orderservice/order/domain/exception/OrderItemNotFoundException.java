package com.skala.orderservice.order.domain.exception;

public class OrderItemNotFoundException extends RuntimeException {

	public OrderItemNotFoundException(String message) {
		super(message);
	}
}
