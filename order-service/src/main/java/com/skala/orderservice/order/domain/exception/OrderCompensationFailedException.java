package com.skala.orderservice.order.domain.exception;

public class OrderCompensationFailedException extends RuntimeException {

	public OrderCompensationFailedException(Throwable orderFailure, Throwable compensationFailure) {
		super("주문 처리 보상에 실패했습니다.", orderFailure);
		addSuppressed(compensationFailure);
	}
}
