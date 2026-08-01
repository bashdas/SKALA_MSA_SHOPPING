package com.skala.orderservice.product.domain.exception;

public class DiscontinuedProductException extends RuntimeException {

	public DiscontinuedProductException(String message) {
		super(message);
	}
}
