package com.skala.orderservice.client.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"clients.user-service.base-url=http://localhost:19081",
		"clients.user-service.connect-timeout=250ms",
		"clients.user-service.read-timeout=4s"
})
@ActiveProfiles("test")
class UserServicePropertiesTest {

	@Autowired
	private UserServiceProperties properties;

	@Test
	void bindsUserServiceProperties() {
		assertThat(properties.baseUrl()).isEqualTo(URI.create("http://localhost:19081"));
		assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
		assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(4));
	}
}
