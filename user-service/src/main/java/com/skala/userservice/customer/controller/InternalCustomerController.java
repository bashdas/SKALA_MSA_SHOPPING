package com.skala.userservice.customer.controller;

import com.skala.userservice.customer.dto.request.PointOperationRequest;
import com.skala.userservice.customer.dto.response.InternalCustomerResponse;
import com.skala.userservice.customer.dto.response.PointOperationResponse;
import com.skala.userservice.customer.service.CustomerPointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerPointService customerPointService;

    @GetMapping("/{customerId}")
    public InternalCustomerResponse getInternalCustomer(@PathVariable Long customerId) {
        return customerPointService.getInternalCustomer(customerId);
    }

    @PostMapping("/{customerId}/points/deduct")
    public PointOperationResponse deductPoint(
            @PathVariable Long customerId,
            @Valid @RequestBody PointOperationRequest request
    ) {
        return customerPointService.deduct(customerId, request);
    }

    @PostMapping("/{customerId}/points/refund")
    public PointOperationResponse refundPoint(
            @PathVariable Long customerId,
            @Valid @RequestBody PointOperationRequest request
    ) {
        return customerPointService.refund(customerId, request);
    }
}
