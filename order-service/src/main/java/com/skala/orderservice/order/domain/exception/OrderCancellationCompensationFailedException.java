package com.skala.orderservice.order.domain.exception;

public class OrderCancellationCompensationFailedException extends RuntimeException {

	public OrderCancellationCompensationFailedException(
			Throwable cancellationFailure, Throwable compensationFailure) {
		super("주문 취소 보상에 실패했습니다.", cancellationFailure);
		addSuppressed(compensationFailure);
	}
}
