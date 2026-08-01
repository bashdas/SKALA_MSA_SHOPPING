package com.skala.orderservice.common.exception;

import com.skala.orderservice.product.domain.exception.DiscontinuedProductException;
import com.skala.orderservice.product.domain.exception.InsufficientStockException;
import com.skala.orderservice.product.domain.exception.InvalidProductNameException;
import com.skala.orderservice.product.domain.exception.InvalidProductPriceException;
import com.skala.orderservice.product.domain.exception.InvalidStockQuantityException;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String INVALID_REQUEST_MESSAGE = "요청 값이 올바르지 않습니다.";

	@ExceptionHandler(ProductNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProductNotFound(
			ProductNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidProductNameException.class)
	public ResponseEntity<ErrorResponse> handleInvalidProductName(
			InvalidProductNameException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_NAME", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidProductPriceException.class)
	public ResponseEntity<ErrorResponse> handleInvalidProductPrice(
			InvalidProductPriceException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_PRICE", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidStockQuantityException.class)
	public ResponseEntity<ErrorResponse> handleInvalidStockQuantity(
			InvalidStockQuantityException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_STOCK_QUANTITY", exception.getMessage(), request);
	}

	@ExceptionHandler(InsufficientStockException.class)
	public ResponseEntity<ErrorResponse> handleInsufficientStock(
			InsufficientStockException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", exception.getMessage(), request);
	}

	@ExceptionHandler(DiscontinuedProductException.class)
	public ResponseEntity<ErrorResponse> handleDiscontinuedProduct(
			DiscontinuedProductException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "DISCONTINUED_PRODUCT", exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_MESSAGE, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_MESSAGE, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected error while handling {}", request.getRequestURI(), exception);
		return error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_SERVER_ERROR",
				"서버 내부 오류가 발생했습니다.",
				request
		);
	}

	private ResponseEntity<ErrorResponse> error(
			HttpStatus status, String code, String message, HttpServletRequest request) {
		ErrorResponse response = new ErrorResponse(
				LocalDateTime.now(), status.value(), code, message, request.getRequestURI());
		return ResponseEntity.status(status).body(response);
	}
}
