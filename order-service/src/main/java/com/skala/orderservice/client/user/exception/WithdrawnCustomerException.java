package com.skala.orderservice.client.user.exception;

public class WithdrawnCustomerException extends RuntimeException {

	public WithdrawnCustomerException() {
		super("탈퇴한 고객은 이용할 수 없습니다.");
	}
}
