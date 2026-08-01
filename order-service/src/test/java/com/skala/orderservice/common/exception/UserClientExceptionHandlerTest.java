package com.skala.orderservice.common.exception;

import com.skala.orderservice.client.user.exception.CustomerNotFoundException;
import com.skala.orderservice.client.user.exception.InsufficientFundsException;
import com.skala.orderservice.client.user.exception.PointRequestConflictException;
import com.skala.orderservice.client.user.exception.UserServiceResponseException;
import com.skala.orderservice.client.user.exception.UserServiceUnavailableException;
import com.skala.orderservice.client.user.exception.WithdrawnCustomerException;
import com.skala.orderservice.product.controller.ProductController;
import com.skala.orderservice.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class UserClientExceptionHandlerTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean ProductService productService;

	@Test
	void mapsCustomerNotFound() throws Exception {
		when(productService.getProduct(1L)).thenThrow(new CustomerNotFoundException());
		perform().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
	}

	@Test
	void mapsWithdrawnCustomer() throws Exception {
		when(productService.getProduct(1L)).thenThrow(new WithdrawnCustomerException());
		perform().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WITHDRAWN_CUSTOMER"));
	}

	@Test
	void mapsInsufficientFunds() throws Exception {
		when(productService.getProduct(1L)).thenThrow(new InsufficientFundsException());
		perform().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
	}

	@Test
	void mapsPointRequestConflict() throws Exception {
		when(productService.getProduct(1L)).thenThrow(new PointRequestConflictException());
		perform().andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("POINT_REQUEST_CONFLICT"));
	}

	@Test
	void mapsUnavailableWithoutExposingInternalCause() throws Exception {
		when(productService.getProduct(1L))
				.thenThrow(new UserServiceUnavailableException(new RuntimeException("secret connection detail")));
		perform().andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE"))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("secret connection detail"))));
	}

	@Test
	void mapsUnexpectedUserServiceResponse() throws Exception {
		when(productService.getProduct(1L)).thenThrow(new UserServiceResponseException());
		perform().andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("USER_SERVICE_ERROR"));
	}

	private org.springframework.test.web.servlet.ResultActions perform() throws Exception {
		return mockMvc.perform(get("/api/products/1"));
	}
}
