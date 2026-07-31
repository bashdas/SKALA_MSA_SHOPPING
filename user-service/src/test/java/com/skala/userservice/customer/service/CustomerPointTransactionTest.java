package com.skala.userservice.customer.service;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.dto.request.PointOperationRequest;
import com.skala.userservice.customer.exception.InsufficientPointException;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.point.repository.PointTransactionRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CustomerPointTransactionTest {

    @Autowired
    private CustomerPointService customerPointService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    private Long customerId;

    @BeforeEach
    void setUp() {
        pointTransactionRepository.deleteAll();
        customerRepository.deleteAll();
        customerId = customerRepository.saveAndFlush(
                Customer.create("skala01", "encoded-password", "박다솔")
        ).getId();
    }

    @Test
    @DisplayName("포인트 처리 이력 저장 실패 시 고객 포인트 변경도 롤백된다")
    void rollbackPointWhenTransactionHistorySaveFails() {
        String tooLongRequestId = "R".repeat(101);

        assertThatThrownBy(() -> customerPointService.deduct(
                customerId,
                new PointOperationRequest(1_000L, tooLongRequestId)
        )).isInstanceOf(ConstraintViolationException.class);

        assertThat(customerRepository.findById(customerId).orElseThrow().getPoint()).isEqualTo(10_000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("포인트 차감 실패 시 처리 이력이 저장되지 않는다")
    void doNotSaveHistoryWhenDeductFails() {
        assertThatThrownBy(() -> customerPointService.deduct(
                customerId,
                new PointOperationRequest(10_001L, "DEDUCT-FAIL")
        )).isInstanceOf(InsufficientPointException.class);

        assertThat(customerRepository.findById(customerId).orElseThrow().getPoint()).isEqualTo(10_000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }
}
