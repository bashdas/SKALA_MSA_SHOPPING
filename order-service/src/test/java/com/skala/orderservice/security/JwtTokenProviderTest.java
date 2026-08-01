package com.skala.orderservice.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

	private static final String SECRET =
			"order-service-jwt-test-secret-key-with-at-least-thirty-two-bytes";
	private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET));

	@Test
	void verifiesUserServiceCompatibleTokenAndExtractsClaims() {
		AuthenticatedCustomer customer = provider.parseAuthenticatedCustomer(token("1", "ACTIVE", SECRET, 60_000));
		assertThat(customer.customerId()).isEqualTo(1L);
		assertThat(customer.loginId()).isEqualTo("customer1");
	}

	@Test void rejectsTamperedToken() {
		String token = token("1", "ACTIVE", SECRET, 60_000);
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token + "x")).isInstanceOf(JwtException.class);
	}

	@Test void rejectsTokenSignedWithAnotherSecret() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("1", "ACTIVE",
				"another-jwt-test-secret-key-with-at-least-thirty-two-bytes", 60_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsExpiredToken() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("1", "ACTIVE", SECRET, -1_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsMissingSubject() {
		String token = Jwts.builder().claim("loginId", "customer1").claim("status", "ACTIVE")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token)).isInstanceOf(JwtException.class);
	}

	@Test void rejectsNonNumericSubject() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("customer", "ACTIVE", SECRET, 60_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsZeroCustomerId() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("0", "ACTIVE", SECRET, 60_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsNegativeCustomerId() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("-1", "ACTIVE", SECRET, 60_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsWithdrawnStatus() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("1", "WITHDRAWN", SECRET, 60_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsMissingStatus() {
		assertThatThrownBy(() -> provider.parseAuthenticatedCustomer(token("1", null, SECRET, 60_000)))
				.isInstanceOf(JwtException.class);
	}

	@Test void rejectsTooShortSecretConfiguration() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new JwtProperties("short"))).isNotEmpty();
		}
	}

	private String token(String subject, String status, String secret, long expirationOffset) {
		var builder = Jwts.builder().subject(subject).claim("loginId", "customer1")
				.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expirationOffset));
		if (status != null) builder.claim("status", status);
		return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}
}
