package com.skala.orderservice.order.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class OrderCancellationPointRequestIdGenerator {

	private final Supplier<UUID> uuidSupplier;

	public OrderCancellationPointRequestIdGenerator() {
		this(UUID::randomUUID);
	}

	OrderCancellationPointRequestIdGenerator(Supplier<UUID> uuidSupplier) {
		this.uuidSupplier = uuidSupplier;
	}

	public OrderCancellationPointRequestIds generate() {
		String operationId = uuidSupplier.get().toString();
		return new OrderCancellationPointRequestIds(
				"ORDER-CANCEL-" + operationId + "-REFUND",
				"ORDER-CANCEL-" + operationId + "-REDEDUCT"
		);
	}
}
