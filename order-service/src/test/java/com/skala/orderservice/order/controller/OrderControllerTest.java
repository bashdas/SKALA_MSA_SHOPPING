package com.skala.orderservice.order.controller;

import com.skala.orderservice.order.domain.OrderStatus;
import com.skala.orderservice.order.domain.exception.ExcessiveCancelQuantityException;
import com.skala.orderservice.order.domain.exception.OrderAlreadyCancelledException;
import com.skala.orderservice.order.domain.exception.OrderNotFoundException;
import com.skala.orderservice.order.dto.response.OrderItemResponse;
import com.skala.orderservice.order.dto.response.OrderResponse;
import com.skala.orderservice.order.service.OrderCreationResult;
import com.skala.orderservice.order.service.OrderService;
import com.skala.orderservice.product.domain.exception.InsufficientStockException;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean OrderService orderService;

	@Test
	void createsNewOrderWith201() throws Exception {
		when(orderService.createOrAddOrder(any())).thenReturn(new OrderCreationResult(response(), true));
		performValidCreate().andExpect(status().isCreated());
	}

	@Test
	void setsLocationForNewOrder() throws Exception {
		when(orderService.createOrAddOrder(any())).thenReturn(new OrderCreationResult(response(), true));
		performValidCreate().andExpect(header().string("Location", "/api/orders/1"));
	}

	@Test
	void returns200WhenAddingToExistingOrder() throws Exception {
		when(orderService.createOrAddOrder(any())).thenReturn(new OrderCreationResult(response(), false));
		performValidCreate().andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidCustomerId() throws Exception {
		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"customerId":0,"items":[{"productId":1,"quantity":1}]}
				"""))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsEmptyItems() throws Exception {
		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
					.content("{\"customerId\":1,\"items\":[]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidProductIdOrQuantity() throws Exception {
		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"customerId":1,"items":[{"productId":0,"quantity":0}]}
				"""))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void mapsInsufficientStockTo409() throws Exception {
		when(orderService.createOrAddOrder(any())).thenThrow(new InsufficientStockException("상품 재고가 부족합니다."));
		performValidCreate().andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
	}

	@Test
	void mapsMissingProductTo404() throws Exception {
		when(orderService.createOrAddOrder(any())).thenThrow(new ProductNotFoundException());
		performValidCreate().andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void getsOrder() throws Exception {
		when(orderService.getOrder(1L)).thenReturn(response());
		mockMvc.perform(get("/api/orders/1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].productName").value("키보드"));
	}

	@Test
	void mapsMissingOrderTo404() throws Exception {
		when(orderService.getOrder(1L)).thenThrow(new OrderNotFoundException());
		mockMvc.perform(get("/api/orders/1")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
	}

	@Test
	void getsCustomerOrders() throws Exception {
		when(orderService.getOrdersByCustomer(1L)).thenReturn(List.of(response()));
		mockMvc.perform(get("/api/orders").param("customerId", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].customerId").value(1));
	}

	@Test
	void partiallyCancelsOrderItem() throws Exception {
		when(orderService.cancelOrderItem(1L, 1L, 1)).thenReturn(response());
		mockMvc.perform(patch("/api/orders/1/items/1/cancel")
					.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
				.andExpect(status().isOk());
	}

	@Test
	void mapsExcessiveCancellationTo409() throws Exception {
		when(orderService.cancelOrderItem(1L, 1L, 10))
				.thenThrow(new ExcessiveCancelQuantityException("주문 수량보다 많이 취소할 수 없습니다."));
		mockMvc.perform(patch("/api/orders/1/items/1/cancel")
					.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXCESSIVE_CANCEL_QUANTITY"));
	}

	@Test
	void cancelsEntireOrderWith204() throws Exception {
		doNothing().when(orderService).cancelOrder(1L);
		mockMvc.perform(patch("/api/orders/1/cancel")).andExpect(status().isNoContent());
	}

	@Test
	void mapsDuplicateCancellationTo409() throws Exception {
		doThrow(new OrderAlreadyCancelledException("이미 취소된 주문입니다."))
				.when(orderService).cancelOrder(1L);
		mockMvc.perform(patch("/api/orders/1/cancel")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"));
	}

	@Test
	void doesNotExposeJpaInternalsOrCircularOrderReference() throws Exception {
		when(orderService.getOrder(1L)).thenReturn(response());
		mockMvc.perform(get("/api/orders/1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderItems").doesNotExist())
				.andExpect(jsonPath("$.items[0].order").doesNotExist())
				.andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
	}

	private org.springframework.test.web.servlet.ResultActions performValidCreate() throws Exception {
		return mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"customerId":1,"items":[{"productId":1,"quantity":2}]}
				"""));
	}

	private OrderResponse response() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
		OrderItemResponse item = new OrderItemResponse(
				1L, "키보드", BigDecimal.valueOf(1_000), 2, BigDecimal.valueOf(2_000));
		return new OrderResponse(
				1L, 1L, OrderStatus.CREATED, BigDecimal.valueOf(2_000), List.of(item), now, now);
	}
}
