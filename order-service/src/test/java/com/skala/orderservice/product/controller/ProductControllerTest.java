package com.skala.orderservice.product.controller;

import com.skala.orderservice.product.domain.ProductStatus;
import com.skala.orderservice.product.domain.exception.DiscontinuedProductException;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import com.skala.orderservice.product.dto.response.ProductPageResponse;
import com.skala.orderservice.product.dto.response.ProductResponse;
import com.skala.orderservice.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import com.skala.orderservice.security.SecurityConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=order-service-jwt-test-secret-key-with-at-least-thirty-two-bytes")
class ProductControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	@Test
	void createsProductWith201() throws Exception {
		when(productService.createProduct(any())).thenReturn(response(ProductStatus.AVAILABLE, 10));

		mockMvc.perform(post("/api/products")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"무선 키보드","price":39000,"stockQuantity":10}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("무선 키보드"));
	}

	@Test
	void setsLocationHeaderAfterCreatingProduct() throws Exception {
		when(productService.createProduct(any())).thenReturn(response(ProductStatus.AVAILABLE, 10));

		mockMvc.perform(post("/api/products")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"무선 키보드","price":39000,"stockQuantity":10}
							"""))
				.andExpect(header().string("Location", "/api/products/1"));
	}

	@Test
	void rejectsInvalidCreateRequest() throws Exception {
		mockMvc.perform(post("/api/products")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":" ","price":-1,"stockQuantity":-1}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void getsProducts() throws Exception {
		ProductPageResponse page = new ProductPageResponse(
				List.of(response(ProductStatus.AVAILABLE, 10)), 0, 20, 1, 1, true, true);
		when(productService.getProducts(any())).thenReturn(page);

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void getsProduct() throws Exception {
		when(productService.getProduct(1L)).thenReturn(response(ProductStatus.AVAILABLE, 10));

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.status").value("AVAILABLE"));
	}

	@Test
	void returns404WhenProductDoesNotExist() throws Exception {
		when(productService.getProduct(1L)).thenThrow(new ProductNotFoundException());

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void updatesProduct() throws Exception {
		when(productService.updateProduct(eq(1L), any())).thenReturn(
				new ProductResponse(1L, "저소음 무선 키보드", BigDecimal.valueOf(42_000), 10,
						ProductStatus.AVAILABLE, LocalDateTime.now(), LocalDateTime.now()));

		mockMvc.perform(patch("/api/products/1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"저소음 무선 키보드","price":42000}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(42000));
	}

	@Test
	void rejectsUpdateForDiscontinuedProduct() throws Exception {
		when(productService.updateProduct(eq(1L), any()))
				.thenThrow(new DiscontinuedProductException("판매 중단된 상품은 수정할 수 없습니다."));

		mockMvc.perform(patch("/api/products/1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"저소음 무선 키보드","price":42000}
							"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DISCONTINUED_PRODUCT"));
	}

	@Test
	void addsStock() throws Exception {
		when(productService.addStock(eq(1L), any())).thenReturn(response(ProductStatus.AVAILABLE, 15));

		mockMvc.perform(post("/api/products/1/stock")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"quantity\":5}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stockQuantity").value(15));
	}

	@Test
	void rejectsInvalidStockQuantity() throws Exception {
		mockMvc.perform(post("/api/products/1/stock")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"quantity\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void discontinuesProductWith204() throws Exception {
		doNothing().when(productService).discontinueProduct(1L);

		mockMvc.perform(delete("/api/products/1"))
				.andExpect(status().isNoContent());
	}

	@Test
	void doesNotExposeEntityImplementationDetails() throws Exception {
		when(productService.getProduct(1L)).thenReturn(response(ProductStatus.AVAILABLE, 10));

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
				.andExpect(jsonPath("$.handler").doesNotExist());
	}

	private ProductResponse response(ProductStatus status, int stockQuantity) {
		LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
		return new ProductResponse(
				1L, "무선 키보드", BigDecimal.valueOf(39_000), stockQuantity, status, now, now);
	}
}
