package com.skala.userservice.customer.service;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.dto.request.CreateCustomerRequest;
import com.skala.userservice.customer.dto.request.UpdateCustomerNameRequest;
import com.skala.userservice.customer.dto.response.CustomerResponse;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.DuplicateLoginIdException;
import com.skala.userservice.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        if (customerRepository.existsByLoginId(request.loginId())) {
            throw new DuplicateLoginIdException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Customer customer = Customer.create(request.loginId(), encodedPassword, request.name());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    public Page<CustomerResponse> getCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable).map(CustomerResponse::from);
    }

    public CustomerResponse getCustomer(Long customerId) {
        return CustomerResponse.from(findCustomer(customerId));
    }

    public CustomerResponse getCustomerByLoginId(String loginId) {
        Customer customer = customerRepository.findByLoginId(loginId)
                .orElseThrow(CustomerNotFoundException::new);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse updateCustomerName(Long customerId, UpdateCustomerNameRequest request) {
        Customer customer = findCustomer(customerId);
        customer.changeName(request.name());
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void withdrawCustomer(Long customerId) {
        findCustomer(customerId).withdraw();
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(CustomerNotFoundException::new);
    }
}
