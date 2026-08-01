package com.skala.orderservice.client.user.exception;

public class InsufficientFundsException extends RuntimeException {

	public InsufficientFundsException() {
		super("포인트가 부족합니다.");
	}
}
