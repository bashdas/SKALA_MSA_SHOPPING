package com.skala.orderservice.product.repository;

import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductRepositoryTest {

	@Autowired
	private ProductRepository productRepository;

	@Test
	void savesAndFindsProductById() {
		Product saved = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(50_000), 10));

		Product found = productRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
	}

	@Test
	void persistsProductFieldsAndStatus() {
		Product saved = productRepository.saveAndFlush(
				Product.create("키보드", new BigDecimal("50000.50"), 10));

		Product found = productRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getName()).isEqualTo("키보드");
		assertThat(found.getPrice()).isEqualByComparingTo("50000.50");
		assertThat(found.getStockQuantity()).isEqualTo(10);
		assertThat(found.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
	}

	@Test
	void findsProductByIdWithPessimisticWriteLock() {
		Product saved = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(50_000), 10));

		Product found = productRepository.findByIdForUpdate(saved.getId()).orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
	}

	@Test
	void setsCreatedAndUpdatedTimestamps() {
		Product saved = productRepository.saveAndFlush(
				Product.create("키보드", BigDecimal.valueOf(50_000), 10));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

		saved.updateInfo("기계식 키보드", BigDecimal.valueOf(70_000));
		productRepository.saveAndFlush(saved);

		assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
	}
}
