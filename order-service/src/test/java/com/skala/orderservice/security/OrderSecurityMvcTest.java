package com.skala.orderservice.security;

import com.skala.orderservice.order.controller.OrderController;
import com.skala.orderservice.order.service.OrderCancellationService;
import com.skala.orderservice.order.service.OrderPlacementService;
import com.skala.orderservice.order.service.OrderService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=order-service-jwt-test-secret-key-with-at-least-thirty-two-bytes")
class OrderSecurityMvcTest {

	private static final String SECRET =
			"order-service-jwt-test-secret-key-with-at-least-thirty-two-bytes";
	@Autowired MockMvc mockMvc;
	@MockitoBean OrderService orderService;
	@MockitoBean OrderPlacementService placementService;
	@MockitoBean OrderCancellationService cancellationService;

	@Test void returnsJson401WithoutToken() throws Exception {
		mockMvc.perform(post("/api/orders"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.path").value("/api/orders"));
	}

	@Test void returnsJson401ForInvalidTokenWithoutLeakingIt() throws Exception {
		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer secret-token-value"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test void distinguishesExpiredToken() throws Exception {
		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + expiredToken()))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
	}

	private String expiredToken() {
		return Jwts.builder().subject("1").claim("loginId", "customer1").claim("status", "ACTIVE")
				.issuedAt(new Date(System.currentTimeMillis() - 120_000))
				.expiration(new Date(System.currentTimeMillis() - 60_000))
				.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}
}
