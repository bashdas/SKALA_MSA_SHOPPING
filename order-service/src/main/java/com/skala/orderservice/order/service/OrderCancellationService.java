package com.skala.orderservice.order.service;

import com.skala.orderservice.client.user.UserServiceClient;
import com.skala.orderservice.order.domain.exception.OrderCancellationCompensationFailedException;
import com.skala.orderservice.order.dto.response.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderCancellationService {

	private static final Logger log = LoggerFactory.getLogger(OrderCancellationService.class);

	private final OrderService orderService;
	private final UserServiceClient userServiceClient;
	private final OrderPointAmountConverter amountConverter;
	private final OrderCancellationPointRequestIdGenerator requestIdGenerator;
	private final TransactionTemplate transactionTemplate;

	public OrderCancellationService(
			OrderService orderService,
			UserServiceClient userServiceClient,
			OrderPointAmountConverter amountConverter,
			OrderCancellationPointRequestIdGenerator requestIdGenerator,
			PlatformTransactionManager transactionManager) {
		this.orderService = orderService;
		this.userServiceClient = userServiceClient;
		this.amountConverter = amountConverter;
		this.requestIdGenerator = requestIdGenerator;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public OrderResponse cancelOrderItem(Long orderId, Long productId, int quantity) {
		OrderCancellationPointRequestIds requestIds = requestIdGenerator.generate();
		RefundState refund = new RefundState();
		try {
			return transactionTemplate.execute(status -> {
				OrderItemCancellationResult result =
						orderService.cancelOrderItemLocally(orderId, productId, quantity);
				long amount = amountConverter.convert(result.refundAmount());
				userServiceClient.refundPoints(
						result.customerId(), amount, requestIds.refundRequestId());
				refund.confirm(result.customerId(), amount);
				return result.response();
			});
		} catch (RuntimeException cancellationFailure) {
			compensateIfConfirmed(refund, requestIds, cancellationFailure);
			throw cancellationFailure;
		}
	}

	public void cancelOrder(Long orderId) {
		OrderCancellationPointRequestIds requestIds = requestIdGenerator.generate();
		RefundState refund = new RefundState();
		try {
			transactionTemplate.executeWithoutResult(status -> {
				OrderCancellationResult result = orderService.cancelOrderLocally(orderId);
				long amount = amountConverter.convert(result.refundAmount());
				userServiceClient.refundPoints(
						result.customerId(), amount, requestIds.refundRequestId());
				refund.confirm(result.customerId(), amount);
			});
		} catch (RuntimeException cancellationFailure) {
			compensateIfConfirmed(refund, requestIds, cancellationFailure);
			throw cancellationFailure;
		}
	}

	private void compensateIfConfirmed(
			RefundState refund,
			OrderCancellationPointRequestIds requestIds,
			RuntimeException cancellationFailure) {
		if (!refund.confirmed()) {
			// 환불 결과가 timeout 등으로 불확실하면 자동 재차감하지 않는다.
			return;
		}
		try {
			userServiceClient.deductPoints(
					refund.customerId(), refund.amount(), requestIds.reDeductRequestId());
		} catch (RuntimeException compensationFailure) {
			// TODO 운영 환경에서는 수동 정산 또는 영속적인 재처리 대상으로 기록해야 한다.
			log.error("Order cancellation compensation failed. refundRequestId={}, reDeductRequestId={}",
					requestIds.refundRequestId(), requestIds.reDeductRequestId(), compensationFailure);
			throw new OrderCancellationCompensationFailedException(
					cancellationFailure, compensationFailure);
		}
	}

	private static final class RefundState {
		private boolean confirmed;
		private Long customerId;
		private long amount;

		void confirm(Long customerId, long amount) {
			this.customerId = customerId;
			this.amount = amount;
			this.confirmed = true;
		}

		boolean confirmed() {
			return confirmed;
		}

		Long customerId() {
			return customerId;
		}

		long amount() {
			return amount;
		}
	}
}
