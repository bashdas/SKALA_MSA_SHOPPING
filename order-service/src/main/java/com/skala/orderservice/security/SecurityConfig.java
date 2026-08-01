package com.skala.orderservice.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
		return new JwtTokenProvider(properties);
	}

	@Bean
	SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) {
		return new SecurityErrorResponseWriter(objectMapper);
	}

	@Bean
	JwtAuthenticationFilter jwtAuthenticationFilter(
			JwtTokenProvider provider, SecurityErrorResponseWriter writer) {
		return new JwtAuthenticationFilter(provider, writer);
	}

	@Bean
	RestAuthenticationEntryPoint restAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
		return new RestAuthenticationEntryPoint(writer);
	}

	@Bean
	RestAccessDeniedHandler restAccessDeniedHandler(SecurityErrorResponseWriter writer) {
		return new RestAccessDeniedHandler(writer);
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtFilter,
			RestAuthenticationEntryPoint entryPoint,
			RestAccessDeniedHandler deniedHandler) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/h2-console/**", "/error").permitAll()
						// TODO 관리자 역할 도입 후 상품 변경 API를 ADMIN 전용으로 제한한다.
						.requestMatchers("/api/products/**").permitAll()
						.requestMatchers("/api/orders/**").authenticated()
						// 현재 공개/인증 API만 명시하고, 기존 비주문 엔드포인트 호환성은 유지한다.
						.anyRequest().permitAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(deniedHandler))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
