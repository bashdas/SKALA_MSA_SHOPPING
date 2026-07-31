package com.skala.userservice.customer.service;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.domain.CustomerStatus;
import com.skala.userservice.customer.dto.request.CreateCustomerRequest;
import com.skala.userservice.customer.dto.request.UpdateCustomerNameRequest;
import com.skala.userservice.customer.dto.response.CustomerResponse;
import com.skala.userservice.customer.exception.CustomerAlreadyWithdrawnException;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.DuplicateLoginIdException;
import com.skala.userservice.customer.exception.WithdrawnCustomerException;
import com.skala.userservice.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        customerService = new CustomerService(customerRepository, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입에 성공한다")
    void createCustomer() {
        CreateCustomerRequest request = new CreateCustomerRequest("skala01", "password123", "박다솔");
        given(customerRepository.existsByLoginId("skala01")).willReturn(false);
        given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.createCustomer(request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(response.point()).isEqualTo(10_000L);
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("회원가입 비밀번호는 평문이 아닌 BCrypt 값으로 저장한다")
    void encodePasswordOnCreateCustomer() {
        CreateCustomerRequest request = new CreateCustomerRequest("skala01", "password123", "박다솔");
        given(customerRepository.existsByLoginId("skala01")).willReturn(false);
        given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> invocation.getArgument(0));

        customerService.createCustomer(request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        String savedPassword = captor.getValue().getPassword();
        assertThat(savedPassword).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedPassword)).isTrue();
    }

    @Test
    @DisplayName("중복 loginId로 가입할 수 없다")
    void cannotCreateDuplicateLoginId() {
        CreateCustomerRequest request = new CreateCustomerRequest("skala01", "password123", "박다솔");
        given(customerRepository.existsByLoginId("skala01")).willReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateLoginIdException.class);
    }

    @Test
    @DisplayName("고객을 단건 조회한다")
    void getCustomer() {
        Customer customer = newCustomer();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response.loginId()).isEqualTo("skala01");
    }

    @Test
    @DisplayName("고객이 없으면 조회에 실패한다")
    void cannotGetMissingCustomer() {
        given(customerRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(1L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("고객 이름을 수정한다")
    void updateCustomerName() {
        Customer customer = newCustomer();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        CustomerResponse response = customerService.updateCustomerName(
                1L,
                new UpdateCustomerNameRequest("변경된 이름")
        );

        assertThat(response.name()).isEqualTo("변경된 이름");
    }

    @Test
    @DisplayName("탈퇴 고객의 이름을 수정할 수 없다")
    void cannotUpdateWithdrawnCustomerName() {
        Customer customer = newCustomer();
        customer.withdraw();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.updateCustomerName(
                1L,
                new UpdateCustomerNameRequest("변경된 이름")
        )).isInstanceOf(WithdrawnCustomerException.class);
    }

    @Test
    @DisplayName("고객을 논리적으로 탈퇴시킨다")
    void withdrawCustomer() {
        Customer customer = newCustomer();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        customerService.withdrawCustomer(1L);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("이미 탈퇴한 고객은 다시 탈퇴할 수 없다")
    void cannotWithdrawCustomerTwice() {
        Customer customer = newCustomer();
        customer.withdraw();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.withdrawCustomer(1L))
                .isInstanceOf(CustomerAlreadyWithdrawnException.class);
    }

    private Customer newCustomer() {
        return Customer.create("skala01", passwordEncoder.encode("password123"), "박다솔");
    }
}
