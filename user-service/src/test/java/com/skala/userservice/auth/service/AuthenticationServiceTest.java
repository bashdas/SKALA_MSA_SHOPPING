package com.skala.userservice.auth.service;

import com.skala.userservice.auth.dto.LoginRequest;
import com.skala.userservice.auth.dto.LoginResponse;
import com.skala.userservice.auth.exception.InvalidCredentialsException;
import com.skala.userservice.auth.exception.WithdrawnCustomerAuthenticationException;
import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.security.JwtProperties;
import com.skala.userservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String TEST_SECRET =
            "authentication-service-test-secret-key-with-at-least-32-bytes";

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtTokenProvider jwtTokenProvider;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(TEST_SECRET, 3600));
        authenticationService = new AuthenticationService(
                customerRepository, passwordEncoder, jwtTokenProvider
        );
    }

    @Test
    @DisplayName("정상 로그인 시 Bearer Access Token을 반환한다")
    void login() {
        Customer customer = customer();
        given(customerRepository.findByLoginId("skala01")).willReturn(Optional.of(customer));
        given(passwordEncoder.matches("password123", customer.getPassword())).willReturn(true);

        LoginResponse response = authenticationService.login(loginRequest());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("발급된 토큰 subject는 customerId이다")
    void tokenSubjectIsCustomerId() {
        Customer customer = customer();
        given(customerRepository.findByLoginId("skala01")).willReturn(Optional.of(customer));
        given(passwordEncoder.matches("password123", customer.getPassword())).willReturn(true);

        LoginResponse response = authenticationService.login(loginRequest());

        assertThat(jwtTokenProvider.parseClaims(response.accessToken()).getSubject()).isEqualTo("1");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 인증에 실패한다")
    void rejectWrongPassword() {
        Customer customer = customer();
        given(customerRepository.findByLoginId("skala01")).willReturn(Optional.of(customer));
        given(passwordEncoder.matches("wrong-password", customer.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("skala01", "wrong-password")
        )).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("존재하지 않는 loginId도 동일한 인증 실패 예외를 사용한다")
    void rejectMissingLoginId() {
        given(customerRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("unknown", "password123")
        )).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("탈퇴 고객의 로그인을 거부한다")
    void rejectWithdrawnCustomer() {
        Customer customer = customer();
        customer.withdraw();
        given(customerRepository.findByLoginId("skala01")).willReturn(Optional.of(customer));
        given(passwordEncoder.matches("password123", customer.getPassword())).willReturn(true);

        assertThatThrownBy(() -> authenticationService.login(loginRequest()))
                .isInstanceOf(WithdrawnCustomerAuthenticationException.class);
    }

    @Test
    @DisplayName("로그인 응답 DTO는 password 필드를 노출하지 않는다")
    void loginResponseDoesNotExposePassword() {
        assertThat(Arrays.stream(LoginResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("password");
    }

    private Customer customer() {
        Customer customer = Customer.create("skala01", "encoded-password", "박다솔");
        ReflectionTestUtils.setField(customer, "id", 1L);
        return customer;
    }

    private LoginRequest loginRequest() {
        return new LoginRequest("skala01", "password123");
    }
}
