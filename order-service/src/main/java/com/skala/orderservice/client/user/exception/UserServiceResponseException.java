package com.skala.orderservice.client.user.exception;

public class UserServiceResponseException extends RuntimeException {

	public UserServiceResponseException() {
		super("고객 서비스 응답을 처리할 수 없습니다.");
	}

	public UserServiceResponseException(Throwable cause) {
		super("고객 서비스 응답을 처리할 수 없습니다.", cause);
	}
}
