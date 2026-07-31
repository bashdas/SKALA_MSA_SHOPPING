package com.skala.userservice.customer.controller;

import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.point.repository.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InternalCustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @BeforeEach
    void setUp() {
        pointTransactionRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 requestId 재전송은 고객 포인트를 한 번만 변경한다")
    void duplicateRequestChangesPointOnce() throws Exception {
        Customer customer = customerRepository.saveAndFlush(
                Customer.create("skala01", "encoded-password", "박다솔")
        );
        String path = "/internal/customers/" + customer.getId() + "/points/deduct";
        String content = "{\"amount\":1000,\"requestId\":\"DEDUCT-ONCE\"}";

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(9_000));
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(9_000));

        Customer reloaded = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(reloaded.getPoint()).isEqualTo(9_000L);
        assertThat(pointTransactionRepository.count()).isEqualTo(1L);
    }
}
