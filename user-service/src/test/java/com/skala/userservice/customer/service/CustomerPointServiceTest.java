package com.skala.userservice.customer.service;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.domain.CustomerStatus;
import com.skala.userservice.customer.dto.request.PointOperationRequest;
import com.skala.userservice.customer.dto.response.InternalCustomerResponse;
import com.skala.userservice.customer.dto.response.PointOperationResponse;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.InsufficientPointException;
import com.skala.userservice.customer.exception.InvalidPointAmountException;
import com.skala.userservice.customer.exception.WithdrawnCustomerException;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.point.domain.PointTransaction;
import com.skala.userservice.point.domain.PointTransactionType;
import com.skala.userservice.point.exception.PointRequestConflictException;
import com.skala.userservice.point.repository.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerPointServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    private CustomerPointService customerPointService;

    @BeforeEach
    void setUp() {
        customerPointService = new CustomerPointService(customerRepository, pointTransactionRepository);
    }

    @Test
    @DisplayName("ACTIVE 고객의 상태와 포인트를 내부 조회한다")
    void getActiveCustomer() {
        Customer customer = customer(1L);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        InternalCustomerResponse response = customerPointService.getInternalCustomer(1L);

        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(response.point()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("탈퇴 고객도 내부 조회할 수 있다")
    void getWithdrawnCustomer() {
        Customer customer = customer(1L);
        customer.withdraw();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        InternalCustomerResponse response = customerPointService.getInternalCustomer(1L);

        assertThat(response.status()).isEqualTo(CustomerStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("없는 고객 내부 조회는 실패한다")
    void cannotGetMissingCustomer() {
        given(customerRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerPointService.getInternalCustomer(1L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("포인트를 차감하고 처리 이력을 저장한다")
    void deduct() {
        mockNewOperation(customer(1L));
        mockTransactionSave();

        PointOperationResponse response = customerPointService.deduct(
                1L, request(2_000L, "DEDUCT-1")
        );

        assertThat(response.balance()).isEqualTo(8_000L);
        assertThat(response.type()).isEqualTo(PointTransactionType.DEDUCT);
    }

    @Test
    @DisplayName("포인트가 부족하면 차감에 실패한다")
    void cannotDeductWithInsufficientPoint() {
        mockNewOperation(customer(1L));

        assertThatThrownBy(() -> customerPointService.deduct(
                1L, request(10_001L, "DEDUCT-1")
        )).isInstanceOf(InsufficientPointException.class);
        verify(pointTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("0 이하의 포인트 차감은 실패한다")
    void cannotDeductInvalidAmount() {
        mockNewOperation(customer(1L));

        assertThatThrownBy(() -> customerPointService.deduct(
                1L, request(0L, "DEDUCT-1")
        )).isInstanceOf(InvalidPointAmountException.class);
    }

    @Test
    @DisplayName("포인트를 환불하고 처리 이력을 저장한다")
    void refund() {
        mockNewOperation(customer(1L));
        mockTransactionSave();

        PointOperationResponse response = customerPointService.refund(
                1L, request(2_000L, "REFUND-1")
        );

        assertThat(response.balance()).isEqualTo(12_000L);
        assertThat(response.type()).isEqualTo(PointTransactionType.REFUND);
    }

    @Test
    @DisplayName("탈퇴 고객의 포인트를 차감할 수 없다")
    void cannotDeductWithdrawnCustomer() {
        Customer customer = customer(1L);
        customer.withdraw();
        mockNewOperation(customer);

        assertThatThrownBy(() -> customerPointService.deduct(
                1L, request(1_000L, "DEDUCT-1")
        )).isInstanceOf(WithdrawnCustomerException.class);
    }

    @Test
    @DisplayName("탈퇴 고객의 포인트를 환불할 수 없다")
    void cannotRefundWithdrawnCustomer() {
        Customer customer = customer(1L);
        customer.withdraw();
        mockNewOperation(customer);

        assertThatThrownBy(() -> customerPointService.refund(
                1L, request(1_000L, "REFUND-1")
        )).isInstanceOf(WithdrawnCustomerException.class);
    }

    @Test
    @DisplayName("동일한 차감 요청은 기존 결과를 반환하고 다시 차감하지 않는다")
    void doNotDeductDuplicateRequest() {
        Customer customer = customer(1L);
        PointTransaction previous = PointTransaction.create(
                1L, "DEDUCT-1", PointTransactionType.DEDUCT, 2_000L, 8_000L
        );
        given(customerRepository.findByIdForUpdate(1L)).willReturn(Optional.of(customer));
        given(pointTransactionRepository.findByRequestId("DEDUCT-1")).willReturn(Optional.of(previous));

        PointOperationResponse response = customerPointService.deduct(
                1L, request(2_000L, "DEDUCT-1")
        );

        assertThat(response.balance()).isEqualTo(8_000L);
        assertThat(customer.getPoint()).isEqualTo(10_000L);
        verify(pointTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동일한 환불 요청은 기존 결과를 반환하고 다시 환불하지 않는다")
    void doNotRefundDuplicateRequest() {
        Customer customer = customer(1L);
        PointTransaction previous = PointTransaction.create(
                1L, "REFUND-1", PointTransactionType.REFUND, 2_000L, 12_000L
        );
        given(customerRepository.findByIdForUpdate(1L)).willReturn(Optional.of(customer));
        given(pointTransactionRepository.findByRequestId("REFUND-1")).willReturn(Optional.of(previous));

        PointOperationResponse response = customerPointService.refund(
                1L, request(2_000L, "REFUND-1")
        );

        assertThat(response.balance()).isEqualTo(12_000L);
        assertThat(customer.getPoint()).isEqualTo(10_000L);
        verify(pointTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동일 requestId의 요청 내용이 다르면 충돌한다")
    void conflictWithDifferentDuplicateRequest() {
        Customer customer = customer(1L);
        PointTransaction previous = PointTransaction.create(
                1L, "REQUEST-1", PointTransactionType.DEDUCT, 2_000L, 8_000L
        );
        given(customerRepository.findByIdForUpdate(1L)).willReturn(Optional.of(customer));
        given(pointTransactionRepository.findByRequestId("REQUEST-1")).willReturn(Optional.of(previous));

        assertThatThrownBy(() -> customerPointService.refund(
                1L, request(2_000L, "REQUEST-1")
        )).isInstanceOf(PointRequestConflictException.class);
    }

    private void mockNewOperation(Customer customer) {
        given(customerRepository.findByIdForUpdate(1L)).willReturn(Optional.of(customer));
        given(pointTransactionRepository.findByRequestId(any())).willReturn(Optional.empty());
    }

    private void mockTransactionSave() {
        given(pointTransactionRepository.saveAndFlush(any(PointTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private Customer customer(Long id) {
        Customer customer = Customer.create("skala01", "encoded-password", "박다솔");
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private PointOperationRequest request(long amount, String requestId) {
        return new PointOperationRequest(amount, requestId);
    }
}
