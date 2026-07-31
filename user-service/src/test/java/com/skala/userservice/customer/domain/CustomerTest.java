package com.skala.userservice.customer.domain;

import com.skala.userservice.customer.exception.CustomerAlreadyWithdrawnException;
import com.skala.userservice.customer.exception.InsufficientPointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    @Test
    @DisplayName("고객을 생성하면 입력값과 활성 상태를 가진다")
    void createCustomer() {
        Customer customer = newCustomer();

        assertThat(customer.getLoginId()).isEqualTo("customer1");
        assertThat(customer.getName()).isEqualTo("홍길동");
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("고객 생성 시 초기 포인트는 10000이다")
    void hasInitialPoint() {
        Customer customer = newCustomer();

        assertThat(customer.getPoint()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("고객 이름을 수정한다")
    void changeName() {
        Customer customer = newCustomer();

        customer.changeName("김길동");

        assertThat(customer.getName()).isEqualTo("김길동");
    }

    @Test
    @DisplayName("고객 탈퇴 시 상태를 WITHDRAWN으로 변경한다")
    void withdraw() {
        Customer customer = newCustomer();

        customer.withdraw();

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("이미 탈퇴한 고객은 다시 탈퇴할 수 없다")
    void cannotWithdrawTwice() {
        Customer customer = newCustomer();
        customer.withdraw();

        assertThatThrownBy(customer::withdraw)
                .isInstanceOf(CustomerAlreadyWithdrawnException.class);
    }

    @Test
    @DisplayName("보유 포인트를 차감한다")
    void deductPoint() {
        Customer customer = newCustomer();

        customer.deductPoint(3_000L);

        assertThat(customer.getPoint()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("잔액보다 많은 포인트를 차감하면 실패한다")
    void cannotDeductMoreThanBalance() {
        Customer customer = newCustomer();

        assertThatThrownBy(() -> customer.deductPoint(10_001L))
                .isInstanceOf(InsufficientPointException.class);
        assertThat(customer.getPoint()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("포인트를 환불한다")
    void refundPoint() {
        Customer customer = newCustomer();
        customer.deductPoint(3_000L);

        customer.refundPoint(3_000L);

        assertThat(customer.getPoint()).isEqualTo(10_000L);
    }

    private Customer newCustomer() {
        return Customer.create("customer1", "encoded-password", "홍길동");
    }
}
