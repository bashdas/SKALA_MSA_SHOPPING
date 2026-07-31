package com.skala.userservice.customer.exception;

public class InvalidPointAmountException extends IllegalArgumentException {

    public InvalidPointAmountException(long amount) {
        super("포인트 금액은 0보다 커야 합니다. amount=" + amount);
    }
}
