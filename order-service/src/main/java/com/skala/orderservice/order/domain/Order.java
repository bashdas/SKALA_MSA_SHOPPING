package com.skala.orderservice.order.domain;

import com.skala.orderservice.common.entity.BaseTimeEntity;
import com.skala.orderservice.order.domain.exception.CancelledOrderException;
import com.skala.orderservice.order.domain.exception.ExcessiveCancelQuantityException;
import com.skala.orderservice.order.domain.exception.InvalidCustomerIdException;
import com.skala.orderservice.order.domain.exception.InvalidOrderQuantityException;
import com.skala.orderservice.order.domain.exception.OrderAlreadyCancelledException;
import com.skala.orderservice.order.domain.exception.OrderItemNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@Column(name = "total_amount", nullable = false)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@OneToMany(
			mappedBy = "order",
			cascade = CascadeType.ALL,
			orphanRemoval = true,
			fetch = FetchType.LAZY
	)
	private List<OrderItem> orderItems = new ArrayList<>();

	protected Order() {
	}

	private Order(Long customerId) {
		validateCustomerId(customerId);
		this.customerId = customerId;
		this.totalAmount = BigDecimal.ZERO;
		this.status = OrderStatus.CREATED;
	}

	public static Order create(Long customerId) {
		return new Order(customerId);
	}

	public void addItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
		validateCreatedOrder();
		OrderItem.validateInputs(productId, productName, unitPrice, quantity);

		findOrderItem(productId).ifPresentOrElse(
				orderItem -> orderItem.increaseQuantity(quantity),
				() -> orderItems.add(OrderItem.create(this, productId, productName, unitPrice, quantity))
		);
		recalculateTotalAmount();
	}

	public void cancelItem(Long productId, int quantity) {
		validateCreatedOrder();
		validateQuantity(quantity);

		OrderItem orderItem = findOrderItem(productId)
				.orElseThrow(() -> new OrderItemNotFoundException("주문에서 상품을 찾을 수 없습니다."));
		if (quantity > orderItem.getQuantity()) {
			throw new ExcessiveCancelQuantityException("주문 수량보다 많이 취소할 수 없습니다.");
		}

		if (quantity == orderItem.getQuantity()) {
			orderItems.remove(orderItem);
			orderItem.removeOrder();
		} else {
			orderItem.decreaseQuantity(quantity);
		}

		if (orderItems.isEmpty()) {
			status = OrderStatus.CANCELLED;
		}
		recalculateTotalAmount();
	}

	public void cancel() {
		if (status == OrderStatus.CANCELLED) {
			throw new OrderAlreadyCancelledException("이미 취소된 주문입니다.");
		}
		orderItems.forEach(OrderItem::removeOrder);
		orderItems.clear();
		totalAmount = BigDecimal.ZERO;
		status = OrderStatus.CANCELLED;
	}

	private java.util.Optional<OrderItem> findOrderItem(Long productId) {
		return orderItems.stream()
				.filter(orderItem -> orderItem.getProductId().equals(productId))
				.findFirst();
	}

	private void recalculateTotalAmount() {
		totalAmount = orderItems.stream()
				.map(OrderItem::getSubtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private void validateCreatedOrder() {
		if (status == OrderStatus.CANCELLED) {
			throw new CancelledOrderException("취소된 주문은 변경할 수 없습니다.");
		}
	}

	private static void validateCustomerId(Long customerId) {
		if (customerId == null || customerId <= 0) {
			throw new InvalidCustomerIdException("고객 ID는 양수여야 합니다.");
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

	public Long getCustomerId() {
		return customerId;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public List<OrderItem> getOrderItems() {
		return Collections.unmodifiableList(orderItems);
	}
}
