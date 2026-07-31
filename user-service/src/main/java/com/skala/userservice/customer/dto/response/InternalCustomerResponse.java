package com.skala.userservice.customer.dto.response;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.domain.CustomerStatus;

public record InternalCustomerResponse(
        Long id,
        CustomerStatus status,
        long point
) {
    public static InternalCustomerResponse from(Customer customer) {
        return new InternalCustomerResponse(customer.getId(), customer.getStatus(), customer.getPoint());
    }
}
