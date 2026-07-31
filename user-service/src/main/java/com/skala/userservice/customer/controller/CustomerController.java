package com.skala.userservice.customer.controller;

import com.skala.userservice.customer.dto.request.CreateCustomerRequest;
import com.skala.userservice.customer.dto.request.UpdateCustomerNameRequest;
import com.skala.userservice.customer.dto.response.CustomerResponse;
import com.skala.userservice.customer.service.CustomerService;
import com.skala.userservice.security.AuthenticatedCustomer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/customers/" + response.id())).body(response);
    }

    @GetMapping
    public Page<CustomerResponse> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return customerService.getCustomers(PageRequest.of(page, size));
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(@PathVariable Long customerId) {
        return customerService.getCustomer(customerId);
    }

    @GetMapping("/by-login-id/{loginId}")
    public CustomerResponse getCustomerByLoginId(@PathVariable String loginId) {
        return customerService.getCustomerByLoginId(loginId);
    }

    @GetMapping("/me")
    public CustomerResponse getMe(@AuthenticationPrincipal AuthenticatedCustomer authenticatedCustomer) {
        return customerService.getCustomer(authenticatedCustomer.customerId());
    }

    @PatchMapping("/{customerId}")
    public CustomerResponse updateCustomerName(
            @PathVariable Long customerId,
            @Valid @RequestBody UpdateCustomerNameRequest request
    ) {
        return customerService.updateCustomerName(customerId, request);
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> withdrawCustomer(@PathVariable Long customerId) {
        customerService.withdrawCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}
