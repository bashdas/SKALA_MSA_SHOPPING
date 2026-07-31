package com.skala.userservice.common.exception;

import com.skala.userservice.auth.exception.InvalidCredentialsException;
import com.skala.userservice.auth.exception.WithdrawnCustomerAuthenticationException;
import com.skala.userservice.customer.exception.CustomerAlreadyWithdrawnException;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.DuplicateLoginIdException;
import com.skala.userservice.customer.exception.InsufficientPointException;
import com.skala.userservice.customer.exception.InvalidPointAmountException;
import com.skala.userservice.customer.exception.WithdrawnCustomerException;
import com.skala.userservice.point.exception.PointRequestConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), request);
    }

    @ExceptionHandler(WithdrawnCustomerAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleWithdrawnCustomerAuthentication(
            WithdrawnCustomerAuthenticationException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.FORBIDDEN, "WITHDRAWN_CUSTOMER", exception.getMessage(), request);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(
            CustomerNotFoundException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateLoginIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLoginId(
            DuplicateLoginIdException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", exception.getMessage(), request);
    }

    @ExceptionHandler(WithdrawnCustomerException.class)
    public ResponseEntity<ErrorResponse> handleWithdrawnCustomer(
            WithdrawnCustomerException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, "WITHDRAWN_CUSTOMER", exception.getMessage(), request);
    }

    @ExceptionHandler(CustomerAlreadyWithdrawnException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyWithdrawnCustomer(
            CustomerAlreadyWithdrawnException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, "CUSTOMER_ALREADY_WITHDRAWN", exception.getMessage(), request);
    }

    @ExceptionHandler(InsufficientPointException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPoint(
            InsufficientPointException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidPointAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPointAmount(
            InvalidPointAmountException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.BAD_REQUEST, "INVALID_POINT_AMOUNT", exception.getMessage(), request);
    }

    @ExceptionHandler(PointRequestConflictException.class)
    public ResponseEntity<ErrorResponse> handlePointRequestConflict(
            PointRequestConflictException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, "POINT_REQUEST_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return createResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> createResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                code,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }
}
