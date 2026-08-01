package com.skala.orderservice.product.domain.exception;

public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException(String message) {
		super(message);
	}
}
