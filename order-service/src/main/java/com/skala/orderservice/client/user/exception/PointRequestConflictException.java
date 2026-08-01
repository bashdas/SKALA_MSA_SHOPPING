package com.skala.orderservice.client.user.exception;

public class PointRequestConflictException extends RuntimeException {

	public PointRequestConflictException() {
		super("포인트 요청이 기존 요청과 충돌합니다.");
	}
}
