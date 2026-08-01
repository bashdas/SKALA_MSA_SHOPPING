package com.skala.orderservice.order.service;

import java.math.BigDecimal;

public record OrderCancellationResult(Long customerId, BigDecimal refundAmount) {
}
