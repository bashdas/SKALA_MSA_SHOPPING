package com.skala.orderservice.order.repository;

import com.skala.orderservice.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@Query("select distinct o from Order o left join fetch o.orderItems where o.id = :orderId")
	Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

	@Query("""
			select distinct o from Order o
			left join fetch o.orderItems
			where o.customerId = :customerId
			order by o.createdAt desc
			""")
	List<Order> findAllByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select distinct o from Order o left join fetch o.orderItems where o.id = :orderId")
	Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select distinct o from Order o
			left join fetch o.orderItems
			where o.id = (
				select max(o2.id) from Order o2
				where o2.customerId = :customerId
				and o2.status = com.skala.orderservice.order.domain.OrderStatus.CREATED
				and o2.createdAt = (
					select max(o3.createdAt) from Order o3
					where o3.customerId = :customerId
					and o3.status = com.skala.orderservice.order.domain.OrderStatus.CREATED
				)
			)
			""")
	Optional<Order> findFirstCreatedOrderByCustomerIdForUpdate(@Param("customerId") Long customerId);
}
