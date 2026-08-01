package com.skala.orderservice.order.domain.exception;

public class InvalidPointAmountException extends RuntimeException {

	public InvalidPointAmountException() {
		super("포인트 차감 금액이 올바르지 않습니다.");
	}
}
