package com.skala.userservice.customer.exception;

public class InsufficientPointException extends IllegalStateException {

    public InsufficientPointException(long currentPoint, long requestedPoint) {
        super("포인트가 부족합니다.");
    }
}
