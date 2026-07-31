package com.skala.userservice.customer.controller;

import com.skala.userservice.common.exception.GlobalExceptionHandler;
import com.skala.userservice.customer.domain.CustomerStatus;
import com.skala.userservice.customer.dto.response.InternalCustomerResponse;
import com.skala.userservice.customer.dto.response.PointOperationResponse;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.InsufficientPointException;
import com.skala.userservice.customer.service.CustomerPointService;
import com.skala.userservice.point.domain.PointTransactionType;
import com.skala.userservice.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalCustomerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InternalCustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerPointService customerPointService;

    @Test
    @DisplayName("고객 내부 조회 시 필요한 정보만 200으로 반환한다")
    void getInternalCustomer() throws Exception {
        given(customerPointService.getInternalCustomer(1L))
                .willReturn(new InternalCustomerResponse(1L, CustomerStatus.ACTIVE, 10_000L));

        mockMvc.perform(get("/internal/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.point").value(10_000))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.loginId").doesNotExist());
    }

    @Test
    @DisplayName("없는 고객 내부 조회 시 404를 반환한다")
    void getMissingInternalCustomer() throws Exception {
        given(customerPointService.getInternalCustomer(1L)).willThrow(new CustomerNotFoundException());

        mockMvc.perform(get("/internal/customers/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("포인트 차감 시 처리 결과를 200으로 반환한다")
    void deductPoint() throws Exception {
        given(customerPointService.deduct(eq(1L), any())).willReturn(
                response("DEDUCT-1", PointTransactionType.DEDUCT, 2_000L, 8_000L)
        );

        mockMvc.perform(post("/internal/customers/1/points/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(2_000L, "DEDUCT-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEDUCT"))
                .andExpect(jsonPath("$.balance").value(8_000));
    }

    @Test
    @DisplayName("포인트 부족 시 409와 INSUFFICIENT_FUNDS를 반환한다")
    void deductInsufficientPoint() throws Exception {
        given(customerPointService.deduct(eq(1L), any()))
                .willThrow(new InsufficientPointException(10_000L, 20_000L));

        mockMvc.perform(post("/internal/customers/1/points/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(20_000L, "DEDUCT-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.path").value("/internal/customers/1/points/deduct"));
    }

    @Test
    @DisplayName("포인트 환불 시 처리 결과를 200으로 반환한다")
    void refundPoint() throws Exception {
        given(customerPointService.refund(eq(1L), any())).willReturn(
                response("REFUND-1", PointTransactionType.REFUND, 2_000L, 12_000L)
        );

        mockMvc.perform(post("/internal/customers/1/points/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(2_000L, "REFUND-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REFUND"))
                .andExpect(jsonPath("$.balance").value(12_000));
    }

    @Test
    @DisplayName("잘못된 포인트 요청은 400을 반환한다")
    void rejectInvalidPointRequest() throws Exception {
        mockMvc.perform(post("/internal/customers/1/points/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(0L, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private PointOperationResponse response(
            String requestId,
            PointTransactionType type,
            long amount,
            long balance
    ) {
        return new PointOperationResponse(1L, requestId, type, amount, balance);
    }

    private String requestJson(long amount, String requestId) {
        return "{\"amount\":" + amount + ",\"requestId\":\"" + requestId + "\"}";
    }
}
