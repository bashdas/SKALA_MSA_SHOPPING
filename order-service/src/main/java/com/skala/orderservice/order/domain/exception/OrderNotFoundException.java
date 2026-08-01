package com.skala.orderservice.order.domain.exception;

public class OrderNotFoundException extends RuntimeException {

	public OrderNotFoundException() {
		super("주문을 찾을 수 없습니다.");
	}
}
