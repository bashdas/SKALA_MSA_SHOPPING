package com.skala.orderservice.order.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class OrderPointRequestIdGenerator {

	private final Supplier<UUID> uuidSupplier;

	public OrderPointRequestIdGenerator() {
		this(UUID::randomUUID);
	}

	OrderPointRequestIdGenerator(Supplier<UUID> uuidSupplier) {
		this.uuidSupplier = uuidSupplier;
	}

	public OrderPointRequestIds generate() {
		String operationId = uuidSupplier.get().toString();
		return new OrderPointRequestIds(
				"ORDER-" + operationId + "-DEDUCT",
				"ORDER-" + operationId + "-REFUND"
		);
	}
}
