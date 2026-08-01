package com.skala.orderservice.common.exception;

import com.skala.orderservice.client.user.exception.CustomerNotFoundException;
import com.skala.orderservice.client.user.exception.InsufficientFundsException;
import com.skala.orderservice.client.user.exception.PointRequestConflictException;
import com.skala.orderservice.client.user.exception.UserServiceResponseException;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import com.skala.orderservice.order.domain.exception.CancelledOrderException;
import com.skala.orderservice.order.domain.exception.ExcessiveCancelQuantityException;
import com.skala.orderservice.order.domain.exception.InvalidCustomerIdException;
import com.skala.orderservice.order.domain.exception.InvalidOrderItemException;
import com.skala.orderservice.order.domain.exception.InvalidOrderQuantityException;
import com.skala.orderservice.order.domain.exception.InvalidPointAmountException;
import com.skala.orderservice.order.domain.exception.OrderAlreadyCancelledException;
import com.skala.orderservice.order.domain.exception.OrderCancellationCompensationFailedException;
import com.skala.orderservice.order.domain.exception.OrderCompensationFailedException;
import com.skala.orderservice.order.domain.exception.OrderItemNotFoundException;
import com.skala.orderservice.order.domain.exception.OrderNotFoundException;
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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

	@ExceptionHandler(OrderNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleOrderNotFound(
			OrderNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(OrderItemNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleOrderItemNotFound(
			OrderItemNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "ORDER_ITEM_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidCustomerIdException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCustomerId(
			InvalidCustomerIdException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER_ID", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidOrderItemException.class)
	public ResponseEntity<ErrorResponse> handleInvalidOrderItem(
			InvalidOrderItemException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ITEM", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidOrderQuantityException.class)
	public ResponseEntity<ErrorResponse> handleInvalidOrderQuantity(
			InvalidOrderQuantityException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_ORDER_QUANTITY", exception.getMessage(), request);
	}

	@ExceptionHandler(ExcessiveCancelQuantityException.class)
	public ResponseEntity<ErrorResponse> handleExcessiveCancelQuantity(
			ExcessiveCancelQuantityException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "EXCESSIVE_CANCEL_QUANTITY", exception.getMessage(), request);
	}

	@ExceptionHandler(CancelledOrderException.class)
	public ResponseEntity<ErrorResponse> handleCancelledOrder(
			CancelledOrderException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "CANCELLED_ORDER", exception.getMessage(), request);
	}

	@ExceptionHandler(OrderAlreadyCancelledException.class)
	public ResponseEntity<ErrorResponse> handleOrderAlreadyCancelled(
			OrderAlreadyCancelledException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "ORDER_ALREADY_CANCELLED", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidPointAmountException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPointAmount(
			InvalidPointAmountException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_POINT_AMOUNT", exception.getMessage(), request);
	}

	@ExceptionHandler(OrderCompensationFailedException.class)
	public ResponseEntity<ErrorResponse> handleOrderCompensationFailed(
			OrderCompensationFailedException exception, HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_COMPENSATION_FAILED", exception.getMessage(), request);
	}

	@ExceptionHandler(OrderCancellationCompensationFailedException.class)
	public ResponseEntity<ErrorResponse> handleOrderCancellationCompensationFailed(
			OrderCancellationCompensationFailedException exception, HttpServletRequest request) {
		return error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"ORDER_CANCELLATION_COMPENSATION_FAILED",
				exception.getMessage(),
				request
		);
	}

	@ExceptionHandler(CustomerNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCustomerNotFound(
			CustomerNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(WithdrawnCustomerException.class)
	public ResponseEntity<ErrorResponse> handleWithdrawnCustomer(
			WithdrawnCustomerException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "WITHDRAWN_CUSTOMER", exception.getMessage(), request);
	}

	@ExceptionHandler(InsufficientFundsException.class)
	public ResponseEntity<ErrorResponse> handleInsufficientFunds(
			InsufficientFundsException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", exception.getMessage(), request);
	}

	@ExceptionHandler(PointRequestConflictException.class)
	public ResponseEntity<ErrorResponse> handlePointRequestConflict(
			PointRequestConflictException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "POINT_REQUEST_CONFLICT", exception.getMessage(), request);
	}

	@ExceptionHandler(UserServiceUnavailableException.class)
	public ResponseEntity<ErrorResponse> handleUserServiceUnavailable(
			UserServiceUnavailableException exception, HttpServletRequest request) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE", exception.getMessage(), request);
	}

	@ExceptionHandler(UserServiceResponseException.class)
	public ResponseEntity<ErrorResponse> handleUserServiceResponse(
			UserServiceResponseException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_GATEWAY, "USER_SERVICE_ERROR", exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_MESSAGE, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_MESSAGE, request);
	}

	@ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
	public ResponseEntity<ErrorResponse> handleRequestParameterValidation(HttpServletRequest request) {
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
