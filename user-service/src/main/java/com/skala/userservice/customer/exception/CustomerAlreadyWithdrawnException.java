package com.skala.userservice.customer.exception;

public class CustomerAlreadyWithdrawnException extends IllegalStateException {

    public CustomerAlreadyWithdrawnException() {
        super("이미 탈퇴한 고객입니다.");
    }
}
