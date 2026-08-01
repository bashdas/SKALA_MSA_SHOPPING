package com.skala.orderservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final SecurityErrorResponseWriter writer;

	public RestAccessDeniedHandler(SecurityErrorResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException exception) throws IOException {
		writer.write(request, response, 403, "FORBIDDEN", "접근 권한이 없습니다.");
	}
}
