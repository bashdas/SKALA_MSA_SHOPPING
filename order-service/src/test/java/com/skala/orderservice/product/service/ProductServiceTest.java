package com.skala.orderservice.product.service;

import com.skala.orderservice.product.domain.Product;
import com.skala.orderservice.product.domain.ProductStatus;
import com.skala.orderservice.product.domain.exception.DiscontinuedProductException;
import com.skala.orderservice.product.domain.exception.ProductNotFoundException;
import com.skala.orderservice.product.dto.request.AddProductStockRequest;
import com.skala.orderservice.product.dto.request.CreateProductRequest;
import com.skala.orderservice.product.dto.request.UpdateProductRequest;
import com.skala.orderservice.product.dto.response.ProductPageResponse;
import com.skala.orderservice.product.dto.response.ProductResponse;
import com.skala.orderservice.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productRepository);
	}

	@Test
	void createsProduct() {
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProductResponse response = productService.createProduct(
				new CreateProductRequest("무선 키보드", BigDecimal.valueOf(39_000), 10));

		assertThat(response.name()).isEqualTo("무선 키보드");
		assertThat(response.price()).isEqualByComparingTo("39000");
		assertThat(response.stockQuantity()).isEqualTo(10);
		assertThat(response.status()).isEqualTo(ProductStatus.AVAILABLE);
		verify(productRepository).save(any(Product.class));
	}

	@Test
	void createsSoldOutProductWhenInitialStockIsZero() {
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProductResponse response = productService.createProduct(
				new CreateProductRequest("무선 키보드", BigDecimal.valueOf(39_000), 0));

		assertThat(response.status()).isEqualTo(ProductStatus.SOLD_OUT);
	}

	@Test
	void getsProducts() {
		Pageable pageable = PageRequest.of(0, 20);
		Product product = product(10);
		when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(product), pageable, 1));

		ProductPageResponse response = productService.getProducts(pageable);

		assertThat(response.content()).hasSize(1);
		assertThat(response.content().getFirst().name()).isEqualTo("무선 키보드");
		assertThat(response.totalElements()).isEqualTo(1);
	}

	@Test
	void getsProduct() {
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(10)));

		ProductResponse response = productService.getProduct(1L);

		assertThat(response.name()).isEqualTo("무선 키보드");
	}

	@Test
	void failsWhenProductDoesNotExist() {
		when(productRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productService.getProduct(1L))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void updatesProductInformation() {
		Product product = product(10);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));

		ProductResponse response = productService.updateProduct(
				1L, new UpdateProductRequest("저소음 무선 키보드", BigDecimal.valueOf(42_000)));

		assertThat(response.name()).isEqualTo("저소음 무선 키보드");
		assertThat(response.price()).isEqualByComparingTo("42000");
	}

	@Test
	void rejectsUpdateForDiscontinuedProduct() {
		Product product = product(10);
		product.discontinue();
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> productService.updateProduct(
				1L, new UpdateProductRequest("저소음 무선 키보드", BigDecimal.valueOf(42_000))))
				.isInstanceOf(DiscontinuedProductException.class);
	}

	@Test
	void addsStockUsingPessimisticLockQuery() {
		Product product = product(10);
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

		ProductResponse response = productService.addStock(1L, new AddProductStockRequest(5));

		assertThat(response.stockQuantity()).isEqualTo(15);
		verify(productRepository).findByIdForUpdate(1L);
	}

	@Test
	void changesSoldOutProductToAvailableAfterAddingStock() {
		Product product = product(0);
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

		ProductResponse response = productService.addStock(1L, new AddProductStockRequest(5));

		assertThat(response.status()).isEqualTo(ProductStatus.AVAILABLE);
	}

	@Test
	void keepsDiscontinuedStatusAfterAddingStock() {
		Product product = product(0);
		product.discontinue();
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

		ProductResponse response = productService.addStock(1L, new AddProductStockRequest(5));

		assertThat(response.stockQuantity()).isEqualTo(5);
		assertThat(response.status()).isEqualTo(ProductStatus.DISCONTINUED);
	}

	@Test
	void discontinuesProduct() {
		Product product = product(10);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));

		productService.discontinueProduct(1L);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
	}

	@Test
	void acceptsRepeatedDiscontinueRequest() {
		Product product = product(10);
		product.discontinue();
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));

		productService.discontinueProduct(1L);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
	}

	@Test
	void failsToDiscontinueMissingProduct() {
		when(productRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productService.discontinueProduct(1L))
				.isInstanceOf(ProductNotFoundException.class);
	}

	private Product product(int stockQuantity) {
		return Product.create("무선 키보드", BigDecimal.valueOf(39_000), stockQuantity);
	}
}
