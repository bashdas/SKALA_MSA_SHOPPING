package com.skala.orderservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityErrorResponseWriter writer;

	public RestAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		writer.write(request, response, 401, "UNAUTHORIZED", "인증이 필요합니다.");
	}
}
