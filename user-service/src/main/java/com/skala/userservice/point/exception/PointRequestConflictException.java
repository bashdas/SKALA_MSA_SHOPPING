package com.skala.userservice.point.exception;

public class PointRequestConflictException extends RuntimeException {

    public PointRequestConflictException() {
        super("동일한 requestId로 다른 포인트 요청을 처리할 수 없습니다.");
    }
}
