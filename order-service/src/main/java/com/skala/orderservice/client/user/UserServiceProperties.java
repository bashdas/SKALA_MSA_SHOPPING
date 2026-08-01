package com.skala.orderservice.client.user;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "clients.user-service")
public record UserServiceProperties(
		@NotNull URI baseUrl,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout
) {
}
