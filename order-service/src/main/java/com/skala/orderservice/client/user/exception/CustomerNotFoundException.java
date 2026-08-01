package com.skala.orderservice.client.user.exception;

public class CustomerNotFoundException extends RuntimeException {

	public CustomerNotFoundException() {
		super("고객을 찾을 수 없습니다.");
	}
}
