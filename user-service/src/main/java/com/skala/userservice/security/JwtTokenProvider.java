package com.skala.userservice.security;

import com.skala.userservice.customer.domain.Customer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Customer customer) {
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plusSeconds(properties.accessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(customer.getId().toString())
                .claim("loginId", customer.getLoginId())
                .claim("status", customer.getStatus().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public AuthenticatedCustomer parseAuthenticatedCustomer(String token) {
        Claims claims = parseClaims(token);
        Long customerId = Long.valueOf(claims.getSubject());
        String loginId = claims.get("loginId", String.class);
        return new AuthenticatedCustomer(customerId, loginId);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationSeconds();
    }
}
