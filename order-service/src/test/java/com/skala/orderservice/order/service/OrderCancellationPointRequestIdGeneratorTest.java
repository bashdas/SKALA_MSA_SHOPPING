package com.skala.orderservice.order.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationPointRequestIdGeneratorTest {

	private static final UUID UUID_VALUE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

	@Test
	void generatesCancellationRequestIdsWithinLengthLimit() {
		OrderCancellationPointRequestIds ids =
				new OrderCancellationPointRequestIdGenerator(() -> UUID_VALUE).generate();

		assertThat(ids.refundRequestId())
				.isEqualTo("ORDER-CANCEL-550e8400-e29b-41d4-a716-446655440000-REFUND")
				.hasSizeLessThan(100);
	}

	@Test
	void sharesUuidBetweenRefundAndReDeduct() {
		OrderCancellationPointRequestIds ids =
				new OrderCancellationPointRequestIdGenerator(() -> UUID_VALUE).generate();

		assertThat(ids.reDeductRequestId())
				.isEqualTo("ORDER-CANCEL-550e8400-e29b-41d4-a716-446655440000-REDEDUCT");
	}
}
