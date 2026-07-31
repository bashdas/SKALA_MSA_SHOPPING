package com.skala.userservice.auth.service;

import com.skala.userservice.auth.dto.LoginRequest;
import com.skala.userservice.auth.dto.LoginResponse;
import com.skala.userservice.auth.exception.InvalidCredentialsException;
import com.skala.userservice.auth.exception.WithdrawnCustomerAuthenticationException;
import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.domain.CustomerStatus;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByLoginId(request.loginId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (customer.getStatus() == CustomerStatus.WITHDRAWN) {
            throw new WithdrawnCustomerAuthenticationException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(customer);
        return LoginResponse.bearer(accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds());
    }
}
