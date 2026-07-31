package com.skala.userservice.customer.exception;

public class DuplicateLoginIdException extends RuntimeException {

    public DuplicateLoginIdException() {
        super("이미 사용 중인 로그인 ID입니다.");
    }
}
