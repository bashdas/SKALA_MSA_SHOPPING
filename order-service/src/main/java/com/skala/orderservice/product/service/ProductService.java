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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional
	public ProductResponse createProduct(CreateProductRequest request) {
		Product product = Product.create(request.name(), request.price(), request.stockQuantity());
		return ProductResponse.from(productRepository.save(product));
	}

	public ProductPageResponse getProducts(Pageable pageable) {
		return ProductPageResponse.from(productRepository.findAll(pageable));
	}

	public ProductResponse getProduct(Long productId) {
		return ProductResponse.from(findProduct(productId));
	}

	@Transactional
	public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
		Product product = findProduct(productId);
		if (product.getStatus() == ProductStatus.DISCONTINUED) {
			throw new DiscontinuedProductException("판매 중단된 상품은 수정할 수 없습니다.");
		}
		product.updateInfo(request.name(), request.price());
		return ProductResponse.from(product);
	}

	@Transactional
	public ProductResponse addStock(Long productId, AddProductStockRequest request) {
		Product product = productRepository.findByIdForUpdate(productId)
				.orElseThrow(ProductNotFoundException::new);
		product.restoreStock(request.quantity());
		return ProductResponse.from(product);
	}

	@Transactional
	public void discontinueProduct(Long productId) {
		Product product = findProduct(productId);
		product.discontinue();
	}

	private Product findProduct(Long productId) {
		return productRepository.findById(productId).orElseThrow(ProductNotFoundException::new);
	}
}
