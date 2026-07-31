package com.skala.userservice.customer.repository;

import com.skala.userservice.customer.domain.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("loginId로 고객을 조회한다")
    void findByLoginId() {
        Customer savedCustomer = customerRepository.save(
                Customer.create("customer1", "encoded-password", "홍길동")
        );

        assertThat(customerRepository.findByLoginId("customer1"))
                .contains(savedCustomer);
    }

    @Test
    @DisplayName("loginId 중복 여부를 확인한다")
    void existsByLoginId() {
        customerRepository.save(Customer.create("customer1", "encoded-password", "홍길동"));

        assertThat(customerRepository.existsByLoginId("customer1")).isTrue();
        assertThat(customerRepository.existsByLoginId("unknown")).isFalse();
    }
}
