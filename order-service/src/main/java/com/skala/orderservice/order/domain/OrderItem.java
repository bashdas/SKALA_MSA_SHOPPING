package com.skala.orderservice.order.domain;

import com.skala.orderservice.order.domain.exception.ExcessiveCancelQuantityException;
import com.skala.orderservice.order.domain.exception.InvalidOrderItemException;
import com.skala.orderservice.order.domain.exception.InvalidOrderQuantityException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
		name = "order_items",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_order_items_order_product",
				columnNames = {"order_id", "product_id"}
		)
)
public class OrderItem {

	private static final int MAX_PRODUCT_NAME_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "product_name", nullable = false, length = MAX_PRODUCT_NAME_LENGTH)
	private String productName;

	@Column(name = "unit_price", nullable = false)
	private BigDecimal unitPrice;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false)
	private BigDecimal subtotal;

	protected OrderItem() {
	}

	static OrderItem create(
			Order order, Long productId, String productName, BigDecimal unitPrice, int quantity) {
		validateInputs(productId, productName, unitPrice, quantity);

		OrderItem orderItem = new OrderItem();
		orderItem.order = order;
		orderItem.productId = productId;
		orderItem.productName = productName;
		orderItem.unitPrice = unitPrice;
		orderItem.quantity = quantity;
		orderItem.recalculateSubtotal();
		return orderItem;
	}

	static void validateInputs(Long productId, String productName, BigDecimal unitPrice, int quantity) {
		validateProductId(productId);
		validateProductName(productName);
		validateUnitPrice(unitPrice);
		validateQuantity(quantity);
	}

	void increaseQuantity(int quantity) {
		validateQuantity(quantity);
		this.quantity += quantity;
		recalculateSubtotal();
	}

	void decreaseQuantity(int quantity) {
		validateQuantity(quantity);
		if (quantity > this.quantity) {
			throw new ExcessiveCancelQuantityException("주문 수량보다 많이 취소할 수 없습니다.");
		}
		this.quantity -= quantity;
		recalculateSubtotal();
	}

	void removeOrder() {
		this.order = null;
	}

	private void recalculateSubtotal() {
		subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

	private static void validateProductId(Long productId) {
		if (productId == null || productId <= 0) {
			throw new InvalidOrderItemException("상품 ID는 양수여야 합니다.");
		}
	}

	private static void validateProductName(String productName) {
		if (productName == null || productName.isBlank() || productName.length() > MAX_PRODUCT_NAME_LENGTH) {
			throw new InvalidOrderItemException("상품명은 공백일 수 없으며 100자 이하여야 합니다.");
		}
	}

	private static void validateUnitPrice(BigDecimal unitPrice) {
		if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new InvalidOrderItemException("상품 단가는 0 이상이어야 합니다.");
		}
	}

	private static void validateQuantity(int quantity) {
		if (quantity <= 0) {
			throw new InvalidOrderQuantityException("주문 수량은 0보다 커야 합니다.");
		}
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public String getProductName() {
		return productName;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}
}
