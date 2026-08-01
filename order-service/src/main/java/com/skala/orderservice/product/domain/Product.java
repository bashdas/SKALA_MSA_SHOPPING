package com.skala.orderservice.product.domain;

import com.skala.orderservice.common.entity.BaseTimeEntity;
import com.skala.orderservice.product.domain.exception.DiscontinuedProductException;
import com.skala.orderservice.product.domain.exception.InsufficientStockException;
import com.skala.orderservice.product.domain.exception.InvalidProductNameException;
import com.skala.orderservice.product.domain.exception.InvalidProductPriceException;
import com.skala.orderservice.product.domain.exception.InvalidStockQuantityException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product extends BaseTimeEntity {

	private static final int MAX_NAME_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = MAX_NAME_LENGTH)
	private String name;

	@Column(nullable = false)
	private BigDecimal price;

	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductStatus status;

	protected Product() {
	}

	private Product(String name, BigDecimal price, int stockQuantity) {
		validateName(name);
		validatePrice(price);
		validateInitialStock(stockQuantity);
		this.name = name;
		this.price = price;
		this.stockQuantity = stockQuantity;
		this.status = stockQuantity > 0 ? ProductStatus.AVAILABLE : ProductStatus.SOLD_OUT;
	}

	public static Product create(String name, BigDecimal price, int stockQuantity) {
		return new Product(name, price, stockQuantity);
	}

	public void updateInfo(String name, BigDecimal price) {
		validateName(name);
		validatePrice(price);
		this.name = name;
		this.price = price;
	}

	public void deductStock(int quantity) {
		validatePositiveQuantity(quantity);
		if (status == ProductStatus.DISCONTINUED) {
			throw new DiscontinuedProductException("판매 중단된 상품의 재고는 차감할 수 없습니다.");
		}
		if (stockQuantity < quantity) {
			throw new InsufficientStockException("상품 재고가 부족합니다.");
		}

		stockQuantity -= quantity;
		if (stockQuantity == 0) {
			status = ProductStatus.SOLD_OUT;
		}
	}

	public void restoreStock(int quantity) {
		validatePositiveQuantity(quantity);
		stockQuantity += quantity;
		if (status == ProductStatus.SOLD_OUT) {
			status = ProductStatus.AVAILABLE;
		}
	}

	public void discontinue() {
		status = ProductStatus.DISCONTINUED;
	}

	private static void validateName(String name) {
		if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
			throw new InvalidProductNameException("상품명은 공백일 수 없으며 100자 이하여야 합니다.");
		}
	}

	private static void validatePrice(BigDecimal price) {
		if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
			throw new InvalidProductPriceException("상품 가격은 0 이상이어야 합니다.");
		}
	}

	private static void validateInitialStock(int stockQuantity) {
		if (stockQuantity < 0) {
			throw new InvalidStockQuantityException("초기 재고는 0 이상이어야 합니다.");
		}
	}

	private static void validatePositiveQuantity(int quantity) {
		if (quantity <= 0) {
			throw new InvalidStockQuantityException("재고 변경 수량은 0보다 커야 합니다.");
		}
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public int getStockQuantity() {
		return stockQuantity;
	}

	public ProductStatus getStatus() {
		return status;
	}
}
