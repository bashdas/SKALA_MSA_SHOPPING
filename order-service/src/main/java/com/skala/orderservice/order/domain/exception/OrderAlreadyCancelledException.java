package com.skala.orderservice.order.domain.exception;

public class OrderAlreadyCancelledException extends RuntimeException {

	public OrderAlreadyCancelledException(String message) {
		super(message);
	}
}
