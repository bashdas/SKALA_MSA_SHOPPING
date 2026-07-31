package com.skala.userservice.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.skala.userservice.customer.domain.Customer;
import com.skala.userservice.customer.repository.CustomerRepository;
import com.skala.userservice.point.repository.PointTransactionRepository;
import com.skala.userservice.security.JwtProperties;
import com.skala.userservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        pointTransactionRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 성공 시 password 없는 Access Token 응답을 반환한다")
    void login() throws Exception {
        signUp();

        mockMvc.perform(loginRequest("password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("아이디 또는 비밀번호 불일치는 401 INVALID_CREDENTIALS를 반환한다")
    void rejectInvalidCredentials() throws Exception {
        signUp();

        mockMvc.perform(loginRequest("wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("탈퇴 고객 로그인은 403 WITHDRAWN_CUSTOMER를 반환한다")
    void rejectWithdrawnCustomerLogin() throws Exception {
        signUp();
        Customer customer = customerRepository.findByLoginId("skala01").orElseThrow();
        customer.withdraw();
        customerRepository.saveAndFlush(customer);

        mockMvc.perform(loginRequest("password123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WITHDRAWN_CUSTOMER"));
    }

    @Test
    @DisplayName("토큰 없이 me 요청 시 401 JSON 오류를 반환한다")
    void getMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/customers/me"));
    }

    @Test
    @DisplayName("정상 토큰으로 me 요청 시 고객 정보를 반환한다")
    void getMeWithValidToken() throws Exception {
        signUp();
        Customer customer = customerRepository.findByLoginId("skala01").orElseThrow();
        String token = jwtTokenProvider.createAccessToken(customer);

        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.loginId").value("skala01"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 토큰으로 me 요청 시 401을 반환한다")
    void getMeWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 토큰으로 me 요청 시 401 TOKEN_EXPIRED를 반환한다")
    void getMeWithExpiredToken() throws Exception {
        signUp();
        Customer customer = customerRepository.findByLoginId("skala01").orElseThrow();
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                new JwtProperties(jwtProperties.secret(), -1)
        );
        String token = expiredProvider.createAccessToken(customer);

        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("회원가입은 토큰 없이 접근할 수 있다")
    void signUpIsPublic() throws Exception {
        signUp();
    }

    @Test
    @DisplayName("내부 API는 현재 토큰 없이 접근할 수 있다")
    void internalApiIsTemporarilyPublic() throws Exception {
        mockMvc.perform(get("/internal/customers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("회원가입, 로그인, Bearer 토큰 me 조회 전체 흐름이 동작한다")
    void signUpLoginAndGetMe() throws Exception {
        signUp();
        MvcResult loginResult = mockMvc.perform(loginRequest("password123"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = JsonPath.read(
                loginResult.getResponse().getContentAsString(), "$.accessToken"
        );

        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("skala01"))
                .andExpect(jsonPath("$.name").value("박다솔"));
    }

    private void signUp() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "skala01",
                                  "password": "password123",
                                  "name": "박다솔"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String password
    ) {
        return post("/api/customers/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "loginId": "skala01",
                          "password": "%s"
                        }
                        """.formatted(password));
    }
}
