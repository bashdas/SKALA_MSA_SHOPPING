package com.skala.userservice.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerNameRequest(
        @NotBlank @Size(max = 50) String name
) {
}
