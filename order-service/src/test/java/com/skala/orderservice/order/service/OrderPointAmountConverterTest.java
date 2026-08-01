package com.skala.orderservice.order.service;

import com.skala.orderservice.order.domain.exception.InvalidPointAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPointAmountConverterTest {

	private final OrderPointAmountConverter converter = new OrderPointAmountConverter();

	@Test
	void convertsIntegerAmountExactly() {
		assertThat(converter.convert(new BigDecimal("1000.00"))).isEqualTo(1_000L);
	}

	@Test
	void rejectsFractionalAmount() {
		assertThatThrownBy(() -> converter.convert(new BigDecimal("1000.50")))
				.isInstanceOf(InvalidPointAmountException.class);
	}

	@Test
	void rejectsAmountOutsideLongRange() {
		assertThatThrownBy(() -> converter.convert(new BigDecimal("9223372036854775808")))
				.isInstanceOf(InvalidPointAmountException.class);
	}

	@Test
	void rejectsNonPositiveAmount() {
		assertThatThrownBy(() -> converter.convert(BigDecimal.ZERO))
				.isInstanceOf(InvalidPointAmountException.class);
		assertThatThrownBy(() -> converter.convert(BigDecimal.valueOf(-1)))
				.isInstanceOf(InvalidPointAmountException.class);
	}
}
