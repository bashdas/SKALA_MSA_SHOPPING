package com.skala.userservice.customer.exception;

public class WithdrawnCustomerException extends IllegalStateException {

    public WithdrawnCustomerException() {
        super("탈퇴한 고객의 정보는 수정할 수 없습니다.");
    }
}
