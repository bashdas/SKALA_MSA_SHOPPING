package com.skala.userservice.customer.dto.response;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.domain.CustomerStatus;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        String loginId,
        String name,
        long point,
        CustomerStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getLoginId(),
                customer.getName(),
                customer.getPoint(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
