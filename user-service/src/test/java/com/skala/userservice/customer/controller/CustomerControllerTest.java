package com.skala.userservice.customer.controller;

import com.skala.userservice.common.exception.GlobalExceptionHandler;
import com.skala.userservice.customer.domain.CustomerStatus;
import com.skala.userservice.customer.dto.response.CustomerResponse;
import com.skala.userservice.customer.exception.CustomerNotFoundException;
import com.skala.userservice.customer.exception.DuplicateLoginIdException;
import com.skala.userservice.customer.service.CustomerService;
import com.skala.userservice.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    @DisplayName("회원가입 시 201과 비밀번호 없는 고객 응답을 반환한다")
    void createCustomer() throws Exception {
        given(customerService.createCustomer(any())).willReturn(customerResponse());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "skala01",
                                  "password": "password123",
                                  "name": "박다솔"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/customers/1"))
                .andExpect(jsonPath("$.loginId").value("skala01"))
                .andExpect(jsonPath("$.point").value(10000))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("회원가입 입력이 잘못되면 400을 반환한다")
    void createCustomerWithInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "", "password": "123", "name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("중복 loginId이면 409를 반환한다")
    void createCustomerWithDuplicateLoginId() throws Exception {
        given(customerService.createCustomer(any())).willThrow(new DuplicateLoginIdException());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "skala01", "password": "password123", "name": "박다솔"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    @DisplayName("고객 전체 조회 시 200을 반환한다")
    void getCustomers() throws Exception {
        given(customerService.getCustomers(any())).willReturn(new PageImpl<>(List.of(customerResponse())));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @DisplayName("고객 단건 조회 시 200을 반환한다")
    void getCustomer() throws Exception {
        given(customerService.getCustomer(1L)).willReturn(customerResponse());

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("박다솔"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("고객 API 응답 JSON에는 password가 포함되지 않는다")
    void responseDoesNotContainPassword() throws Exception {
        given(customerService.getCustomer(1L)).willReturn(customerResponse());

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password")
                )));
    }

    @Test
    @DisplayName("없는 고객 조회 시 404 오류 응답을 반환한다")
    void getMissingCustomer() throws Exception {
        given(customerService.getCustomer(1L)).willThrow(new CustomerNotFoundException());

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/customers/1"));
    }

    @Test
    @DisplayName("고객 이름 수정 시 200을 반환한다")
    void updateCustomerName() throws Exception {
        CustomerResponse updated = new CustomerResponse(
                1L, "skala01", "변경된 이름", 10_000L, CustomerStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(customerService.updateCustomerName(eq(1L), any())).willReturn(updated);

        mockMvc.perform(patch("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경된 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("변경된 이름"));
    }

    @Test
    @DisplayName("회원 탈퇴 시 204를 반환한다")
    void withdrawCustomer() throws Exception {
        doNothing().when(customerService).withdrawCustomer(1L);

        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private CustomerResponse customerResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new CustomerResponse(
                1L, "skala01", "박다솔", 10_000L, CustomerStatus.ACTIVE, now, now
        );
    }
}
