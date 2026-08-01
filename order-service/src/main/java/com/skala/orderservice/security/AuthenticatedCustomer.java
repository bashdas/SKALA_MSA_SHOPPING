package com.skala.orderservice.security;

public record AuthenticatedCustomer(Long customerId, String loginId) {
}
