package com.skala.userservice.security;

public record AuthenticatedCustomer(
        Long customerId,
        String loginId
) {
}
