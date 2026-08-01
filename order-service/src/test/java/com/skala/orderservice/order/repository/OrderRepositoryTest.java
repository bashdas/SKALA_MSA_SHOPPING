package com.skala.orderservice.order.repository;

import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderRepositoryTest {

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void savesOrderAndMultipleItemsByCascade() {
		Order saved = orderRepository.saveAndFlush(order(1L));
		entityManager.clear();

		Order found = orderRepository.findByIdWithItems(saved.getId()).orElseThrow();

		assertThat(found.getOrderItems()).hasSize(2);
		assertThat(found.getTotalAmount()).isEqualByComparingTo("5000");
		assertThat(found.getOrderItems()).allMatch(item -> item.getId() != null);
	}

	@Test
	void fetchesOrderItemsTogether() {
		Order saved = orderRepository.saveAndFlush(order(1L));
		entityManager.clear();

		Order found = orderRepository.findByIdWithItems(saved.getId()).orElseThrow();

		assertThat(Hibernate.isInitialized(found.getOrderItems())).isTrue();
		assertThat(found.getOrderItems()).hasSize(2);
	}

	@Test
	void findsCustomerOrdersByCreatedAtDescending() {
		Order first = orderRepository.saveAndFlush(order(1L));
		Order second = orderRepository.saveAndFlush(order(1L));
		orderRepository.saveAndFlush(order(2L));
		entityManager.clear();

		List<Order> found = orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(1L);

		assertThat(found).extracting(Order::getId).containsExactly(second.getId(), first.getId());
		assertThat(found).allMatch(order -> Hibernate.isInitialized(order.getOrderItems()));
	}

	@Test
	void findsOrderForUpdateWithItems() {
		Order saved = orderRepository.saveAndFlush(order(1L));
		entityManager.clear();

		Order found = orderRepository.findByIdForUpdate(saved.getId()).orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(Hibernate.isInitialized(found.getOrderItems())).isTrue();
	}

	@Test
	void findsLatestCreatedCustomerOrderForUpdate() {
		Order first = orderRepository.saveAndFlush(order(1L));
		Order second = orderRepository.saveAndFlush(order(1L));
		Order cancelled = order(1L);
		cancelled.cancel();
		orderRepository.saveAndFlush(cancelled);
		entityManager.clear();

		Order found = orderRepository.findFirstCreatedOrderByCustomerIdForUpdate(1L).orElseThrow();

		assertThat(found.getId()).isEqualTo(second.getId());
		assertThat(found.getId()).isNotEqualTo(first.getId());
		assertThat(found.getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(Hibernate.isInitialized(found.getOrderItems())).isTrue();
	}

	@Test
	void removesOrphanedOrderItemFromDatabase() {
		Order saved = orderRepository.saveAndFlush(order(1L));

		saved.cancelItem(1L, 2);
		orderRepository.flush();
		entityManager.clear();

		Order found = orderRepository.findByIdWithItems(saved.getId()).orElseThrow();
		Integer itemCount = jdbcTemplate.queryForObject(
				"select count(*) from order_items where order_id = ?", Integer.class, saved.getId());
		assertThat(found.getOrderItems()).hasSize(1);
		assertThat(itemCount).isEqualTo(1);
	}

	@Test
	void enforcesUniqueProductWithinOrder() {
		Order saved = orderRepository.saveAndFlush(order(1L));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into order_items
				(order_id, product_id, product_name, unit_price, quantity, subtotal)
				values (?, ?, ?, ?, ?, ?)
				""", saved.getId(), 1L, "중복 키보드", BigDecimal.valueOf(1_000), 1,
				BigDecimal.valueOf(1_000)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void setsCreatedAndUpdatedTimestamps() {
		Order saved = orderRepository.saveAndFlush(Order.create(1L));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

		saved.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 1);
		orderRepository.flush();

		assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
	}

	private Order order(Long customerId) {
		Order order = Order.create(customerId);
		order.addItem(1L, "키보드", BigDecimal.valueOf(1_000), 2);
		order.addItem(2L, "마우스", BigDecimal.valueOf(3_000), 1);
		return order;
	}
}
