package com.skala.userservice.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PointOperationRequest(
        @Positive long amount,
        @NotBlank @Size(max = 100) String requestId
) {
}
