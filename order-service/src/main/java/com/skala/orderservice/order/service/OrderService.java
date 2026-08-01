package com.skala.orderservice.order.service;

import com.skala.orderservice.order.domain.Order;
import com.skala.orderservice.order.domain.OrderItem;
import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.domain.exception.CancelledOrderException;
import com.skala.orderservice.order.domain.exception.OrderAlreadyCancelledException;
import com.skala.orderservice.order.domain.exception.OrderItemNotFoundException;
import com.skala.orderservice.order.domain.exception.OrderNotFoundException;
import com.skala.orderservice.order.dto.request.CreateOrderItemRequest;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import com.skala.orderservice.order.dto.response.OrderResponse;
import com.skala.orderservice.order.repository.OrderRepository;
import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import com.skala.orderservice.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;

	public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
	}

	@Transactional
	public OrderCreationResult createOrAddOrder(CreateOrderRequest request) {
		Map<Long, Integer> quantitiesByProductId = aggregateQuantities(request.items());
		Map<Long, Product> lockedProducts = lockProducts(quantitiesByProductId.keySet());

		quantitiesByProductId.forEach((productId, quantity) ->
				lockedProducts.get(productId).deductStock(quantity));

		Order order = orderRepository.findFirstCreatedOrderByCustomerIdForUpdate(request.customerId())
				.orElse(null);
		boolean created = order == null;
		if (created) {
			order = Order.create(request.customerId());
		}
		BigDecimal beforeTotalAmount = order.getTotalAmount();

		for (Map.Entry<Long, Integer> entry : quantitiesByProductId.entrySet()) {
			Product product = lockedProducts.get(entry.getKey());
			order.addItem(product.getId(), product.getName(), product.getPrice(), entry.getValue());
		}

		Order savedOrder = orderRepository.save(order);
		BigDecimal increasedAmount = savedOrder.getTotalAmount().subtract(beforeTotalAmount);
		return new OrderCreationResult(OrderResponse.from(savedOrder), created, increasedAmount);
	}

	public OrderResponse getOrder(Long orderId) {
		Order order = orderRepository.findByIdWithItems(orderId)
				.orElseThrow(OrderNotFoundException::new);
		return OrderResponse.from(order);
	}

	public List<OrderResponse> getOrdersByCustomer(Long customerId) {
		return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
				.map(OrderResponse::from)
				.toList();
	}

	@Transactional
	public OrderResponse cancelOrderItem(Long orderId, Long productId, int quantity) {
		return cancelOrderItemLocally(orderId, productId, quantity).response();
	}

	@Transactional
	public OrderItemCancellationResult cancelOrderItemLocally(Long orderId, Long productId, int quantity) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(OrderNotFoundException::new);
		if (order.getStatus() == OrderStatus.CANCELLED) {
			throw new CancelledOrderException("취소된 주문은 변경할 수 없습니다.");
		}

		OrderItem orderItem = order.getOrderItems().stream()
				.filter(item -> item.getProductId().equals(productId))
				.findFirst()
				.orElseThrow(() -> new OrderItemNotFoundException("주문에서 상품을 찾을 수 없습니다."));
		BigDecimal refundAmount = orderItem.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
		Long customerId = order.getCustomerId();

		Product product = productRepository.findByIdForUpdate(orderItem.getProductId())
				.orElseThrow(ProductNotFoundException::new);
		order.cancelItem(productId, quantity);
		product.restoreStock(quantity);
		return new OrderItemCancellationResult(
				customerId, refundAmount, OrderResponse.from(order));
	}

	@Transactional
	public void cancelOrder(Long orderId) {
		cancelOrderLocally(orderId);
	}

	@Transactional
	public OrderCancellationResult cancelOrderLocally(Long orderId) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(OrderNotFoundException::new);
		if (order.getStatus() == OrderStatus.CANCELLED) {
			throw new OrderAlreadyCancelledException("이미 취소된 주문입니다.");
		}
		Long customerId = order.getCustomerId();
		BigDecimal refundAmount = order.getTotalAmount();

		List<RestockTarget> restockTargets = order.getOrderItems().stream()
				.map(item -> new RestockTarget(item.getProductId(), item.getQuantity()))
				.sorted(java.util.Comparator.comparing(RestockTarget::productId))
				.toList();

		for (RestockTarget target : restockTargets) {
			Product product = productRepository.findByIdForUpdate(target.productId())
					.orElseThrow(ProductNotFoundException::new);
			product.restoreStock(target.quantity());
		}
		order.cancel();
		return new OrderCancellationResult(customerId, refundAmount);
	}

	private Map<Long, Integer> aggregateQuantities(List<CreateOrderItemRequest> items) {
		Map<Long, Integer> quantities = new TreeMap<>();
		for (CreateOrderItemRequest item : items) {
			quantities.merge(item.productId(), item.quantity(), Math::addExact);
		}
		return quantities;
	}

	private Map<Long, Product> lockProducts(Iterable<Long> productIds) {
		Map<Long, Product> products = new LinkedHashMap<>();
		for (Long productId : productIds) {
			Product product = productRepository.findByIdForUpdate(productId)
					.orElseThrow(ProductNotFoundException::new);
			products.put(productId, product);
		}
		return products;
	}

	private record RestockTarget(Long productId, int quantity) {
	}
}
