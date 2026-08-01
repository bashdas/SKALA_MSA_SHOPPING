package com.skala.orderservice.order.service;

import com.skala.orderservice.order.domain.exception.InvalidPointAmountException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderPointAmountConverter {

	public long convert(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new InvalidPointAmountException();
		}
		try {
			return amount.longValueExact();
		} catch (ArithmeticException exception) {
			throw new InvalidPointAmountException();
		}
	}
}
