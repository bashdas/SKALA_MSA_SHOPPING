package com.skala.orderservice.product.domain.exception;

public class InvalidProductPriceException extends RuntimeException {

	public InvalidProductPriceException(String message) {
		super(message);
	}
}
