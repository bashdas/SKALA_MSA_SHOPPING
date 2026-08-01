package com.skala.orderservice.client.user;

import com.skala.orderservice.client.user.dto.CustomerStatus;
import com.skala.orderservice.client.user.dto.PointOperationType;
import com.skala.orderservice.client.user.exception.CustomerNotFoundException;
import com.skala.orderservice.client.user.exception.InsufficientFundsException;
import com.skala.orderservice.client.user.exception.PointRequestConflictException;
import com.skala.orderservice.client.user.exception.UserServiceResponseException;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceClientTest {

	private MockRestServiceServer server;
	private UserServiceClient client;
	private HttpServer httpServer;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service.test");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new UserServiceClient(builder.build(), JsonMapper.builder().build());
	}

	@AfterEach
	void tearDown() {
		if (httpServer != null) {
			httpServer.stop(0);
		}
	}

	@Test
	void parsesActiveCustomerResponseAndUsesCorrectPath() {
		server.expect(once(), requestTo("http://user-service.test/internal/customers/1"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"id":1,"status":"ACTIVE","point":10000}
						""", MediaType.APPLICATION_JSON));

		var response = client.getCustomer(1L);

		assertThat(response.id()).isEqualTo(1L);
		assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
		assertThat(response.point()).isEqualTo(10_000);
		server.verify();
	}

	@Test
	void parsesWithdrawnCustomerResponse() {
		server.expect(requestTo("http://user-service.test/internal/customers/1"))
				.andRespond(withSuccess("""
						{"id":1,"status":"WITHDRAWN","point":0}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getCustomer(1L).status()).isEqualTo(CustomerStatus.WITHDRAWN);
	}

	@Test
	void mapsCustomerNotFound() {
		expectError(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND");
		assertThatThrownBy(() -> client.getCustomer(1L)).isInstanceOf(CustomerNotFoundException.class);
	}

	@Test
	void mapsWithdrawnCustomerError() {
		expectError(HttpStatus.CONFLICT, "WITHDRAWN_CUSTOMER");
		assertThatThrownBy(() -> client.getCustomer(1L)).isInstanceOf(WithdrawnCustomerException.class);
	}

	@Test
	void deductsPointsWithJsonRequest() {
		server.expect(requestTo("http://user-service.test/internal/customers/1/points/deduct"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", containsString(MediaType.APPLICATION_JSON_VALUE)))
				.andExpect(content().string(containsString("\"amount\":5000")))
				.andExpect(content().string(containsString("\"requestId\":\"ORDER-1-DEDUCT\"")))
				.andRespond(withSuccess("""
						{"customerId":1,"requestId":"ORDER-1-DEDUCT","type":"DEDUCT","amount":5000,"balance":5000}
						""", MediaType.APPLICATION_JSON));

		var response = client.deductPoints(1L, 5_000, "ORDER-1-DEDUCT");

		assertThat(response.type()).isEqualTo(PointOperationType.DEDUCT);
		assertThat(response.balance()).isEqualTo(5_000);
	}

	@Test
	void mapsInsufficientFunds() {
		expectPointError("deduct", "INSUFFICIENT_FUNDS");
		assertThatThrownBy(() -> client.deductPoints(1L, 5_000, "ORDER-1-DEDUCT"))
				.isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	void mapsDeductRequestConflict() {
		expectPointError("deduct", "POINT_REQUEST_CONFLICT");
		assertThatThrownBy(() -> client.deductPoints(1L, 5_000, "ORDER-1-DEDUCT"))
				.isInstanceOf(PointRequestConflictException.class);
	}

	@Test
	void refundsPoints() {
		server.expect(requestTo("http://user-service.test/internal/customers/1/points/refund"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"customerId":1,"requestId":"ORDER-1-REFUND","type":"REFUND","amount":5000,"balance":10000}
						""", MediaType.APPLICATION_JSON));

		var response = client.refundPoints(1L, 5_000, "ORDER-1-REFUND");

		assertThat(response.type()).isEqualTo(PointOperationType.REFUND);
		assertThat(response.balance()).isEqualTo(10_000);
	}

	@Test
	void mapsRefundRequestConflict() {
		expectPointError("refund", "POINT_REQUEST_CONFLICT");
		assertThatThrownBy(() -> client.refundPoints(1L, 5_000, "ORDER-1-REFUND"))
				.isInstanceOf(PointRequestConflictException.class);
	}

	@Test
	void mapsConnectionFailureToUnavailable() {
		UserServiceClient unavailableClient = actualClient("http://127.0.0.1:1", Duration.ofMillis(100));

		assertThatThrownBy(() -> unavailableClient.getCustomer(1L))
				.isInstanceOf(UserServiceUnavailableException.class);
	}

	@Test
	void mapsReadTimeoutToUnavailable() throws Exception {
		httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		httpServer.createContext("/internal/customers/1", exchange -> {
			try {
				Thread.sleep(300);
				exchange.sendResponseHeaders(200, -1);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		httpServer.start();
		UserServiceClient timeoutClient = actualClient(
				"http://127.0.0.1:" + httpServer.getAddress().getPort(), Duration.ofMillis(50));

		assertThatThrownBy(() -> timeoutClient.getCustomer(1L))
				.isInstanceOf(UserServiceUnavailableException.class);
	}

	@Test
	void maps500ToUnavailable() {
		server.expect(requestTo("http://user-service.test/internal/customers/1"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertThatThrownBy(() -> client.getCustomer(1L)).isInstanceOf(UserServiceUnavailableException.class);
	}

	@Test
	void mapsUnknown4xxToUserServiceError() {
		expectError(HttpStatus.BAD_REQUEST, "UNKNOWN_ERROR");
		assertThatThrownBy(() -> client.getCustomer(1L)).isInstanceOf(UserServiceResponseException.class);
	}

	@Test
	void safelyHandlesNonJsonError() {
		server.expect(requestTo("http://user-service.test/internal/customers/1"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.body("<html>bad request</html>").contentType(MediaType.TEXT_HTML));

		assertThatThrownBy(() -> client.getCustomer(1L)).isInstanceOf(UserServiceResponseException.class)
				.hasMessageNotContaining("<html>");
	}

	@Test
	void rejectsEmptySuccessfulResponse() {
		server.expect(requestTo("http://user-service.test/internal/customers/1"))
				.andRespond(withSuccess());
		assertThatThrownBy(() -> client.getCustomer(1L)).isInstanceOf(UserServiceResponseException.class);
	}

	private void expectError(HttpStatus status, String code) {
		server.expect(requestTo("http://user-service.test/internal/customers/1"))
				.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorBody(code)));
	}

	private void expectPointError(String operation, String code) {
		server.expect(requestTo("http://user-service.test/internal/customers/1/points/" + operation))
				.andRespond(withStatus(HttpStatus.CONFLICT)
						.contentType(MediaType.APPLICATION_JSON).body(errorBody(code)));
	}

	private String errorBody(String code) {
		return """
				{"timestamp":"2026-08-01T12:00:00","status":409,"code":"%s","message":"internal","path":"/internal"}
				""".formatted(code);
	}

	private UserServiceClient actualClient(String baseUrl, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		RestClient restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
		return new UserServiceClient(restClient, JsonMapper.builder().build());
	}
}
