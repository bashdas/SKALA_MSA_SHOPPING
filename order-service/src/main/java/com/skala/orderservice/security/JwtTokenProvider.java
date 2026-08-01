package com.skala.orderservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class JwtTokenProvider {

	private final SecretKey signingKey;

	public JwtTokenProvider(JwtProperties properties) {
		this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public AuthenticatedCustomer parseAuthenticatedCustomer(String token) {
		Claims claims = parseClaims(token);
		String subject = claims.getSubject();
		if (subject == null || subject.isBlank()) {
			throw new JwtException("Invalid subject");
		}

		Long customerId;
		try {
			customerId = Long.valueOf(subject);
		} catch (NumberFormatException exception) {
			throw new JwtException("Invalid subject", exception);
		}
		if (customerId <= 0) {
			throw new JwtException("Invalid subject");
		}
		if (!"ACTIVE".equals(claims.get("status", String.class))) {
			throw new JwtException("Inactive customer");
		}
		return new AuthenticatedCustomer(customerId, claims.get("loginId", String.class));
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
