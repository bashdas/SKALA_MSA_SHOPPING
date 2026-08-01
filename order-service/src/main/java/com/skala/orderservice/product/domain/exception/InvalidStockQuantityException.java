package com.skala.orderservice.product.domain.exception;

public class InvalidStockQuantityException extends RuntimeException {

	public InvalidStockQuantityException(String message) {
		super(message);
	}
}
