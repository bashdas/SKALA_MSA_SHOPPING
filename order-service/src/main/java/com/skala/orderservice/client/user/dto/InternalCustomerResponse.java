package com.skala.orderservice.client.user.dto;

public record InternalCustomerResponse(Long id, CustomerStatus status, long point) {
}
