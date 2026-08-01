package com.skala.orderservice.client.user;

import com.skala.orderservice.client.user.dto.InternalCustomerResponse;
import com.skala.orderservice.client.user.dto.PointOperationRequest;
import com.skala.orderservice.client.user.dto.PointOperationResponse;
import com.skala.orderservice.client.user.dto.UserServiceErrorResponse;
import com.skala.orderservice.client.user.exception.CustomerNotFoundException;
import com.skala.orderservice.client.user.exception.InsufficientFundsException;
import com.skala.orderservice.client.user.exception.PointRequestConflictException;
import com.skala.orderservice.client.user.exception.UserServiceResponseException;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class UserServiceClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public UserServiceClient(
			@Qualifier("userServiceRestClient") RestClient restClient,
			ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public InternalCustomerResponse getCustomer(Long customerId) {
		return execute(() -> restClient.get()
				.uri("/internal/customers/{customerId}", customerId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::handleError)
				.body(InternalCustomerResponse.class));
	}

	public PointOperationResponse deductPoints(Long customerId, long amount, String requestId) {
		return operatePoints(customerId, amount, requestId, "deduct");
	}

	public PointOperationResponse refundPoints(Long customerId, long amount, String requestId) {
		return operatePoints(customerId, amount, requestId, "refund");
	}

	private PointOperationResponse operatePoints(
			Long customerId, long amount, String requestId, String operation) {
		PointOperationRequest request = new PointOperationRequest(amount, requestId);
		return execute(() -> restClient.post()
				.uri("/internal/customers/{customerId}/points/{operation}", customerId, operation)
				.body(request)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::handleError)
				.body(PointOperationResponse.class));
	}

	private <T> T execute(ClientCall<T> call) {
		try {
			T response = call.execute();
			if (response == null) {
				throw new UserServiceResponseException();
			}
			return response;
		} catch (CustomerNotFoundException | WithdrawnCustomerException | InsufficientFundsException
				| PointRequestConflictException | UserServiceUnavailableException
				| UserServiceResponseException exception) {
			throw exception;
		} catch (ResourceAccessException exception) {
			throw new UserServiceUnavailableException(exception);
		} catch (RestClientException exception) {
			throw new UserServiceResponseException(exception);
		}
	}

	private void handleError(org.springframework.http.HttpRequest request,
			org.springframework.http.client.ClientHttpResponse response) throws IOException {
		if (response.getStatusCode().is5xxServerError()) {
			throw new UserServiceUnavailableException();
		}

		UserServiceErrorResponse errorResponse;
		try {
			errorResponse = objectMapper.readValue(response.getBody(), UserServiceErrorResponse.class);
		} catch (Exception exception) {
			throw new UserServiceResponseException(exception);
		}

		String code = errorResponse.code();
		if ("CUSTOMER_NOT_FOUND".equals(code)) {
			throw new CustomerNotFoundException();
		}
		if ("WITHDRAWN_CUSTOMER".equals(code)) {
			throw new WithdrawnCustomerException();
		}
		if ("INSUFFICIENT_FUNDS".equals(code)) {
			throw new InsufficientFundsException();
		}
		if ("POINT_REQUEST_CONFLICT".equals(code)) {
			throw new PointRequestConflictException();
		}
		throw new UserServiceResponseException();
	}

	@FunctionalInterface
	private interface ClientCall<T> {
		T execute();
	}
}
