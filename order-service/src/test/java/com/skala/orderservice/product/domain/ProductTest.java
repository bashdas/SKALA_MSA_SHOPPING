package com.skala.orderservice.product.domain;

import com.skala.orderservice.product.domain.exception.DiscontinuedProductException;
import com.skala.orderservice.product.domain.exception.InsufficientStockException;
import com.skala.orderservice.product.domain.exception.InvalidProductNameException;
import com.skala.orderservice.product.domain.exception.InvalidProductPriceException;
import com.skala.orderservice.product.domain.exception.InvalidStockQuantityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

	@Test
	void createsAvailableProductWhenStockExists() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 10);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
	}

	@Test
	void createsSoldOutProductWhenStockIsZero() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 0);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
	}

	@Test
	void rejectsInvalidProductName() {
		assertThatThrownBy(() -> Product.create(null, BigDecimal.ZERO, 0))
				.isInstanceOf(InvalidProductNameException.class);
		assertThatThrownBy(() -> Product.create("   ", BigDecimal.ZERO, 0))
				.isInstanceOf(InvalidProductNameException.class);
		assertThatThrownBy(() -> Product.create("가".repeat(101), BigDecimal.ZERO, 0))
				.isInstanceOf(InvalidProductNameException.class);
	}

	@Test
	void rejectsNegativePrice() {
		assertThatThrownBy(() -> Product.create("키보드", BigDecimal.valueOf(-1), 0))
				.isInstanceOf(InvalidProductPriceException.class);
	}

	@Test
	void rejectsNegativeInitialStock() {
		assertThatThrownBy(() -> Product.create("키보드", BigDecimal.ZERO, -1))
				.isInstanceOf(InvalidStockQuantityException.class);
	}

	@Test
	void updatesProductInformation() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 10);

		product.updateInfo("기계식 키보드", BigDecimal.valueOf(70_000));

		assertThat(product.getName()).isEqualTo("기계식 키보드");
		assertThat(product.getPrice()).isEqualByComparingTo("70000");
	}

	@Test
	void deductsStock() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 10);

		product.deductStock(3);

		assertThat(product.getStockQuantity()).isEqualTo(7);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
	}

	@Test
	void changesStatusToSoldOutWhenStockBecomesZero() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 3);

		product.deductStock(3);

		assertThat(product.getStockQuantity()).isZero();
		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
	}

	@Test
	void rejectsDeductionExceedingCurrentStock() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 2);

		assertThatThrownBy(() -> product.deductStock(3))
				.isInstanceOf(InsufficientStockException.class);
		assertThat(product.getStockQuantity()).isEqualTo(2);
	}

	@Test
	void rejectsNonPositiveDeductionQuantity() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 2);

		assertThatThrownBy(() -> product.deductStock(0))
				.isInstanceOf(InvalidStockQuantityException.class);
		assertThatThrownBy(() -> product.deductStock(-1))
				.isInstanceOf(InvalidStockQuantityException.class);
	}

	@Test
	void restoresStock() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 2);

		product.restoreStock(3);

		assertThat(product.getStockQuantity()).isEqualTo(5);
	}

	@Test
	void changesSoldOutProductToAvailableWhenStockIsRestored() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 0);

		product.restoreStock(3);

		assertThat(product.getStockQuantity()).isEqualTo(3);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
	}

	@Test
	void rejectsStockDeductionForDiscontinuedProduct() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 10);
		product.discontinue();

		assertThatThrownBy(() -> product.deductStock(1))
				.isInstanceOf(DiscontinuedProductException.class);
		assertThat(product.getStockQuantity()).isEqualTo(10);
	}

	@Test
	void discontinuesProductIdempotently() {
		Product product = Product.create("키보드", BigDecimal.valueOf(50_000), 10);

		product.discontinue();
		product.discontinue();

		assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
	}
}
