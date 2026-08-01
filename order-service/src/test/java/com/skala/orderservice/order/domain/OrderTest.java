package com.skala.orderservice.order.domain;

import com.skala.orderservice.order.domain.exception.CancelledOrderException;
import com.skala.orderservice.order.domain.exception.ExcessiveCancelQuantityException;
import com.skala.orderservice.order.domain.exception.InvalidCustomerIdException;
import com.skala.orderservice.order.domain.exception.InvalidOrderItemException;
import com.skala.orderservice.order.domain.exception.InvalidOrderQuantityException;
import com.skala.orderservice.order.domain.exception.OrderAlreadyCancelledException;
import com.skala.orderservice.order.domain.exception.OrderItemNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

	@Test
	void createsOrderWithCustomerIdAndCreatedStatus() {
		Order order = Order.create(1L);

		assertThat(order.getCustomerId()).isEqualTo(1L);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void rejectsInvalidCustomerId() {
		assertThatThrownBy(() -> Order.create(null)).isInstanceOf(InvalidCustomerIdException.class);
		assertThatThrownBy(() -> Order.create(0L)).isInstanceOf(InvalidCustomerIdException.class);
		assertThatThrownBy(() -> Order.create(-1L)).isInstanceOf(InvalidCustomerIdException.class);
	}

	@Test
	void addsNewItem() {
		Order order = Order.create(1L);

		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);

		assertThat(order.getOrderItems()).hasSize(1);
		assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(2);
	}

	@Test
	void addsMultipleKindsOfItems() {
		Order order = orderWithTwoItems();

		assertThat(order.getOrderItems()).hasSize(2);
		assertThat(order.getOrderItems()).extracting(OrderItem::getProductId).containsExactly(1L, 2L);
	}

	@Test
	void accumulatesQuantityForSameProduct() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);

		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 3);

		assertThat(order.getOrderItems()).hasSize(1);
		assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(5);
		assertThat(order.getOrderItems().getFirst().getSubtotal()).isEqualByComparingTo("5000");
	}

	@Test
	void preservesSnapshotWhenSameProductIsAddedAgain() {
		Order order = Order.create(1L);
		order.addItem(1L, "기존 상품명", BigDecimal.valueOf(1_000), 2);

		order.addItem(1L, "변경된 상품명", BigDecimal.valueOf(2_000), 3);

		OrderItem item = order.getOrderItems().getFirst();
		assertThat(item.getProductName()).isEqualTo("기존 상품명");
		assertThat(item.getUnitPrice()).isEqualByComparingTo("1000");
		assertThat(item.getSubtotal()).isEqualByComparingTo("5000");
	}

	@Test
	void calculatesTotalAmountFromItemSubtotals() {
		Order order = orderWithTwoItems();

		assertThat(order.getTotalAmount()).isEqualByComparingTo("5000");
	}

	@Test
	void partiallyCancelsItemQuantity() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 5);

		order.cancelItem(1L, 2);

		assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(3);
	}

	@Test
	void recalculatesSubtotalAndTotalAfterPartialCancellation() {
		Order order = orderWithTwoItems();

		order.cancelItem(1L, 1);

		assertThat(order.getOrderItems().getFirst().getSubtotal()).isEqualByComparingTo("1000");
		assertThat(order.getTotalAmount()).isEqualByComparingTo("4000");
	}

	@Test
	void removesItemWhenItsEntireQuantityIsCancelled() {
		Order order = orderWithTwoItems();

		order.cancelItem(1L, 2);

		assertThat(order.getOrderItems()).hasSize(1);
		assertThat(order.getOrderItems().getFirst().getProductId()).isEqualTo(2L);
	}

	@Test
	void cancelsOrderWhenLastItemIsRemoved() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);

		order.cancelItem(1L, 2);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void failsToCancelMissingItem() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);

		assertThatThrownBy(() -> order.cancelItem(2L, 1))
				.isInstanceOf(OrderItemNotFoundException.class);
	}

	@Test
	void rejectsCancellationExceedingOrderedQuantity() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);

		assertThatThrownBy(() -> order.cancelItem(1L, 3))
				.isInstanceOf(ExcessiveCancelQuantityException.class);
	}

	@Test
	void rejectsNonPositiveAddQuantity() {
		Order order = Order.create(1L);

		assertThatThrownBy(() -> order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 0))
				.isInstanceOf(InvalidOrderQuantityException.class);
		assertThatThrownBy(() -> order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), -1))
				.isInstanceOf(InvalidOrderQuantityException.class);
	}

	@Test
	void rejectsNonPositiveCancelQuantity() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);

		assertThatThrownBy(() -> order.cancelItem(1L, 0))
				.isInstanceOf(InvalidOrderQuantityException.class);
		assertThatThrownBy(() -> order.cancelItem(1L, -1))
				.isInstanceOf(InvalidOrderQuantityException.class);
	}

	@Test
	void cancelsEntireOrder() {
		Order order = orderWithTwoItems();

		order.cancel();

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void clearsItemsAndAmountWhenEntireOrderIsCancelled() {
		Order order = orderWithTwoItems();

		order.cancel();

		assertThat(order.getOrderItems()).isEmpty();
		assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void rejectsDuplicateEntireCancellation() {
		Order order = orderWithTwoItems();
		order.cancel();

		assertThatThrownBy(order::cancel).isInstanceOf(OrderAlreadyCancelledException.class);
	}

	@Test
	void rejectsAddingItemToCancelledOrder() {
		Order order = orderWithTwoItems();
		order.cancel();

		assertThatThrownBy(() -> order.addItem(3L, "마우스", BigDecimal.valueOf(500), 1))
				.isInstanceOf(CancelledOrderException.class);
	}

	@Test
	void rejectsPartialCancellationOnCancelledOrder() {
		Order order = orderWithTwoItems();
		order.cancel();

		assertThatThrownBy(() -> order.cancelItem(1L, 1))
				.isInstanceOf(CancelledOrderException.class);
	}

	@Test
	void rejectsInvalidProductId() {
		Order order = Order.create(1L);

		assertThatThrownBy(() -> order.addItem(null, "키보드", BigDecimal.ZERO, 1))
				.isInstanceOf(InvalidOrderItemException.class);
		assertThatThrownBy(() -> order.addItem(0L, "키보드", BigDecimal.ZERO, 1))
				.isInstanceOf(InvalidOrderItemException.class);
	}

	@Test
	void rejectsInvalidProductName() {
		Order order = Order.create(1L);

		assertThatThrownBy(() -> order.addItem(1L, null, BigDecimal.ZERO, 1))
				.isInstanceOf(InvalidOrderItemException.class);
		assertThatThrownBy(() -> order.addItem(1L, " ", BigDecimal.ZERO, 1))
				.isInstanceOf(InvalidOrderItemException.class);
		assertThatThrownBy(() -> order.addItem(1L, "가".repeat(101), BigDecimal.ZERO, 1))
				.isInstanceOf(InvalidOrderItemException.class);
	}

	@Test
	void rejectsInvalidUnitPrice() {
		Order order = Order.create(1L);

		assertThatThrownBy(() -> order.addItem(1L, "키보드", null, 1))
				.isInstanceOf(InvalidOrderItemException.class);
		assertThatThrownBy(() -> order.addItem(1L, "키보드", BigDecimal.valueOf(-1), 1))
				.isInstanceOf(InvalidOrderItemException.class);
	}

	@Test
	void exposesReadOnlyOrderItemCollection() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 1);

		assertThatThrownBy(() -> order.getOrderItems().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	private Order orderWithTwoItems() {
		Order order = Order.create(1L);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);
		order.addItem(2L, "마우스", BigDecimal.valueOf(3_000), 1);
		return order;
	}
}
