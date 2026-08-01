package com.skala.orderservice.order.service;

import com.skala.orderservice.client.user.UserServiceClient;
import com.skala.orderservice.client.user.dto.CustomerStatus;
import com.skala.orderservice.client.user.dto.InternalCustomerResponse;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import com.skala.orderservice.order.domain.exception.OrderCompensationFailedException;
import com.skala.orderservice.order.dto.request.CreateOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderPlacementService {

	private static final Logger log = LoggerFactory.getLogger(OrderPlacementService.class);

	private final OrderService orderService;
	private final UserServiceClient userServiceClient;
	private final OrderPointAmountConverter amountConverter;
	private final OrderPointRequestIdGenerator requestIdGenerator;
	private final TransactionTemplate transactionTemplate;

	public OrderPlacementService(
			OrderService orderService,
			UserServiceClient userServiceClient,
			OrderPointAmountConverter amountConverter,
			OrderPointRequestIdGenerator requestIdGenerator,
			PlatformTransactionManager transactionManager) {
		this.orderService = orderService;
		this.userServiceClient = userServiceClient;
		this.amountConverter = amountConverter;
		this.requestIdGenerator = requestIdGenerator;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public OrderCreationResult placeOrder(CreateOrderRequest request) {
		InternalCustomerResponse customer = userServiceClient.getCustomer(request.customerId());
		if (customer.status() != CustomerStatus.ACTIVE) {
			throw new WithdrawnCustomerException();
		}

		OrderPointRequestIds requestIds = requestIdGenerator.generate();
		DeductionState deduction = new DeductionState();

		try {
			return transactionTemplate.execute(status -> {
				OrderCreationResult result = orderService.createOrAddOrder(request);
				long amount = amountConverter.convert(result.increasedAmount());
				userServiceClient.deductPoints(
						request.customerId(), amount, requestIds.deductRequestId());
				deduction.confirm(amount);
				return result;
			});
		} catch (RuntimeException orderFailure) {
			if (!deduction.confirmed()) {
				throw orderFailure;
			}
			try {
				userServiceClient.refundPoints(
						request.customerId(), deduction.amount(), requestIds.refundRequestId());
			} catch (RuntimeException compensationFailure) {
				// TODO 운영 환경에서는 수동 정산 또는 영속적인 재처리 대상으로 기록해야 한다.
				log.error("Order compensation failed. deductRequestId={}, refundRequestId={}",
						requestIds.deductRequestId(), requestIds.refundRequestId(), compensationFailure);
				throw new OrderCompensationFailedException(orderFailure, compensationFailure);
			}
			throw orderFailure;
		}
	}

	private static final class DeductionState {
		private boolean confirmed;
		private long amount;

		void confirm(long amount) {
			this.amount = amount;
			this.confirmed = true;
		}

		boolean confirmed() {
			return confirmed;
		}

		long amount() {
			return amount;
		}
	}
}
