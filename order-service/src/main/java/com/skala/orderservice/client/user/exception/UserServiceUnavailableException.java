package com.skala.orderservice.client.user.exception;

public class UserServiceUnavailableException extends RuntimeException {

	public UserServiceUnavailableException() {
		super("고객 서비스를 사용할 수 없습니다.");
	}

	public UserServiceUnavailableException(Throwable cause) {
		super("고객 서비스를 사용할 수 없습니다.", cause);
	}
}
