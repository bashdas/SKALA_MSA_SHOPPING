package com.skala.userservice.auth.exception;

public class WithdrawnCustomerAuthenticationException extends RuntimeException {

    public WithdrawnCustomerAuthenticationException() {
        super("탈퇴한 고객은 로그인할 수 없습니다.");
    }
}
