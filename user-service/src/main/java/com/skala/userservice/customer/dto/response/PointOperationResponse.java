package com.skala.userservice.customer.dto.response;

import com.skala.userservice.point.domain.PointTransaction;
import com.skala.userservice.point.domain.PointTransactionType;

public record PointOperationResponse(
        Long customerId,
        String requestId,
        PointTransactionType type,
        long amount,
        long balance
) {
    public static PointOperationResponse from(PointTransaction transaction) {
        return new PointOperationResponse(
                transaction.getCustomerId(),
                transaction.getRequestId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter()
        );
    }
}
