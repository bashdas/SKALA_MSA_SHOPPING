package com.skala.userservice.customer.service;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.domain.CustomerStatus;
import com.skala.userservice.customer.dto.request.PointOperationRequest;
import com.skala.userservice.customer.dto.response.InternalCustomerResponse;
import com.skala.userservice.customer.dto.response.PointOperationResponse;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.WithdrawnCustomerException;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.point.domain.PointTransaction;
import com.skala.userservice.point.domain.PointTransactionType;
import com.skala.userservice.point.exception.PointRequestConflictException;
import com.skala.userservice.point.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerPointService {

    private final CustomerRepository customerRepository;
    private final PointTransactionRepository pointTransactionRepository;

    public InternalCustomerResponse getInternalCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(CustomerNotFoundException::new);
        return InternalCustomerResponse.from(customer);
    }

    @Transactional
    public PointOperationResponse deduct(Long customerId, PointOperationRequest request) {
        return operate(customerId, request, PointTransactionType.DEDUCT);
    }

    @Transactional
    public PointOperationResponse refund(Long customerId, PointOperationRequest request) {
        return operate(customerId, request, PointTransactionType.REFUND);
    }

    private PointOperationResponse operate(
            Long customerId,
            PointOperationRequest request,
            PointTransactionType type
    ) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(CustomerNotFoundException::new);

        PointTransaction previous = pointTransactionRepository.findByRequestId(request.requestId())
                .orElse(null);
        if (previous != null) {
            validateSameRequest(previous, customerId, type, request.amount());
            return PointOperationResponse.from(previous);
        }

        if (customer.getStatus() == CustomerStatus.WITHDRAWN) {
            throw new WithdrawnCustomerException();
        }

        if (type == PointTransactionType.DEDUCT) {
            customer.deductPoint(request.amount());
        } else {
            customer.refundPoint(request.amount());
        }

        PointTransaction transaction = PointTransaction.create(
                customerId,
                request.requestId(),
                type,
                request.amount(),
                customer.getPoint()
        );

        try {
            return PointOperationResponse.from(pointTransactionRepository.saveAndFlush(transaction));
        } catch (DataIntegrityViolationException exception) {
            throw new PointRequestConflictException();
        }
    }

    private void validateSameRequest(
            PointTransaction transaction,
            Long customerId,
            PointTransactionType type,
            long amount
    ) {
        if (!transaction.matches(customerId, type, amount)) {
            throw new PointRequestConflictException();
        }
    }
}
