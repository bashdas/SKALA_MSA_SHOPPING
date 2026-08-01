package com.skala.orderservice.product.controller;

import com.skala.orderservice.product.dto.request.AddProductStockRequest;
import com.skala.orderservice.product.dto.request.CreateProductRequest;
import com.skala.orderservice.product.dto.request.UpdateProductRequest;
import com.skala.orderservice.product.dto.response.ProductPageResponse;
import com.skala.orderservice.product.dto.response.ProductResponse;
import com.skala.orderservice.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
		ProductResponse response = productService.createProduct(request);
		return ResponseEntity.created(URI.create("/api/products/" + response.id())).body(response);
	}

	@GetMapping
	public ProductPageResponse getProducts(
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
		return productService.getProducts(pageable);
	}

	@GetMapping("/{productId}")
	public ProductResponse getProduct(@PathVariable Long productId) {
		return productService.getProduct(productId);
	}

	@PatchMapping("/{productId}")
	public ProductResponse updateProduct(
			@PathVariable Long productId,
			@Valid @RequestBody UpdateProductRequest request) {
		return productService.updateProduct(productId, request);
	}

	@PostMapping("/{productId}/stock")
	public ProductResponse addStock(
			@PathVariable Long productId,
			@Valid @RequestBody AddProductStockRequest request) {
		return productService.addStock(productId, request);
	}

	@DeleteMapping("/{productId}")
	public ResponseEntity<Void> discontinueProduct(@PathVariable Long productId) {
		productService.discontinueProduct(productId);
		return ResponseEntity.noContent().build();
	}
}
