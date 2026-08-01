package com.skala.orderservice.order.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPointRequestIdGeneratorTest {

	@Test
	void generatesExpectedRequestIdFormatWithinLengthLimit() {
		OrderPointRequestIds ids = new OrderPointRequestIdGenerator(() ->
				UUID.fromString("550e8400-e29b-41d4-a716-446655440000")).generate();

		assertThat(ids.deductRequestId())
				.isEqualTo("ORDER-550e8400-e29b-41d4-a716-446655440000-DEDUCT")
				.hasSizeLessThanOrEqualTo(100);
	}

	@Test
	void sharesSameUuidBetweenDeductAndRefund() {
		OrderPointRequestIds ids = new OrderPointRequestIdGenerator(() ->
				UUID.fromString("550e8400-e29b-41d4-a716-446655440000")).generate();

		assertThat(ids.refundRequestId())
				.isEqualTo("ORDER-550e8400-e29b-41d4-a716-446655440000-REFUND");
	}
}
