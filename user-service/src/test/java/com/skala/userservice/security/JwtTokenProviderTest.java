package com.skala.userservice.security;

import com.skala.userservice.customer.domain.Customer;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "jwt-provider-test-secret-key-with-at-least-thirty-two-bytes";

    private final JwtTokenProvider provider = provider(SECRET, 3600);

    @Test
    @DisplayName("정상 Access Token을 생성하고 검증한다")
    void createAndValidateToken() {
        String token = provider.createAccessToken(customer());

        assertThat(provider.parseClaims(token).getSubject()).isEqualTo("1");
        assertThat(provider.parseClaims(token).get("status", String.class)).isEqualTo("ACTIVE");
        assertThat(provider.parseClaims(token).getIssuedAt()).isNotNull();
        assertThat(provider.parseClaims(token).getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("토큰에서 customerId를 추출한다")
    void extractCustomerId() {
        AuthenticatedCustomer principal = provider.parseAuthenticatedCustomer(
                provider.createAccessToken(customer())
        );

        assertThat(principal.customerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("토큰에서 loginId를 추출한다")
    void extractLoginId() {
        AuthenticatedCustomer principal = provider.parseAuthenticatedCustomer(
                provider.createAccessToken(customer())
        );

        assertThat(principal.loginId()).isEqualTo("skala01");
    }

    @Test
    @DisplayName("변조된 토큰을 거부한다")
    void rejectTamperedToken() {
        String token = provider.createAccessToken(customer());
        String[] parts = token.split("\\.");
        parts[1] = (parts[1].charAt(0) == 'a' ? "b" : "a") + parts[1].substring(1);
        String tampered = String.join(".", parts);

        assertThatThrownBy(() -> provider.parseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("다른 secret으로 서명한 토큰을 거부한다")
    void rejectTokenSignedWithDifferentSecret() {
        JwtTokenProvider anotherProvider = provider(
                "another-jwt-test-secret-key-with-at-least-thirty-two-bytes", 3600
        );
        String token = anotherProvider.createAccessToken(customer());

        assertThatThrownBy(() -> provider.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료된 토큰을 거부한다")
    void rejectExpiredToken() {
        JwtTokenProvider expiredProvider = provider(SECRET, -1);
        String token = expiredProvider.createAccessToken(customer());

        assertThatThrownBy(() -> expiredProvider.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private JwtTokenProvider provider(String secret, long expirationSeconds) {
        return new JwtTokenProvider(new JwtProperties(secret, expirationSeconds));
    }

    private Customer customer() {
        Customer customer = Customer.create("skala01", "encoded-password", "박다솔");
        ReflectionTestUtils.setField(customer, "id", 1L);
        return customer;
    }
}
