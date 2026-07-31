package com.skala.userservice.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(min = 4, max = 100) String password,
        @NotBlank @Size(max = 50) String name
) {
}
