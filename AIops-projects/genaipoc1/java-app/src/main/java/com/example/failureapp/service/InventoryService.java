package com.example.failureapp.service;

import com.example.failureapp.model.Product;
import com.example.failureapp.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "#id")
    public Optional<Product> getProductById(Long id) {
        log.debug("Fetching product from database (cache miss): {}", id);
        return productRepository.findById(id);
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> getLowStockProducts(Integer threshold) {
        return productRepository.findByStockQuantityLessThan(threshold);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public Product reserveStock(Long productId, Integer quantity) {
        log.info("Attempting to reserve {} units of product {}", quantity, productId);

        Product product = productRepository.findByIdWithOptimisticLock(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        if (product.getStockQuantity() < quantity) {
            log.error("Insufficient stock for product {}: available={}, requested={}",
                    productId, product.getStockQuantity(), quantity);
            throw new RuntimeException("Insufficient stock");
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);

        try {
            return productRepository.save(product);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure - concurrent modification detected for product {}", productId);
            throw new RuntimeException("Product was modified by another transaction. Please retry.", e);
        }
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public Product releaseStock(Long productId, Integer quantity) {
        log.info("Releasing {} units of product {}", quantity, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        product.setStockQuantity(product.getStockQuantity() + quantity);
        return productRepository.save(product);
    }

    @Transactional
    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product.getName());
        return productRepository.save(product);
    }
}
