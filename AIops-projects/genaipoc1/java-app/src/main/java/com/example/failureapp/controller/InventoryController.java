package com.example.failureapp.controller;

import com.example.failureapp.model.Product;
import com.example.failureapp.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@Slf4j
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest request) {
        try {
            Product product = Product.builder()
                    .name(request.name)
                    .description(request.description)
                    .price(request.price)
                    .stockQuantity(request.stockQuantity)
                    .category(request.category)
                    .build();

            Product createdProduct = inventoryService.createProduct(product);
            log.info("Product created: id={}, name={}", createdProduct.getId(), createdProduct.getName());

            return ResponseEntity.ok(createdProduct);
        } catch (Exception e) {
            log.error("Error creating product: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return inventoryService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/products/category/{category}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable String category) {
        List<Product> products = inventoryService.getProductsByCategory(category);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts(@RequestParam(defaultValue = "10") Integer threshold) {
        List<Product> products = inventoryService.getLowStockProducts(threshold);
        return ResponseEntity.ok(products);
    }

    @PostMapping("/products/{id}/reserve")
    public ResponseEntity<Map<String, Object>> reserveStock(
            @PathVariable Long id,
            @RequestBody StockUpdateRequest request) {
        try {
            Product product = inventoryService.reserveStock(id, request.quantity);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("productId", product.getId());
            response.put("remainingStock", product.getStockQuantity());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error reserving stock for product {}: {}", id, e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/products/{id}/release")
    public ResponseEntity<Map<String, Object>> releaseStock(
            @PathVariable Long id,
            @RequestBody StockUpdateRequest request) {
        try {
            Product product = inventoryService.releaseStock(id, request.quantity);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("productId", product.getId());
            response.put("currentStock", product.getStockQuantity());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error releasing stock for product {}: {}", id, e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // DTOs
    public static class CreateProductRequest {
        public String name;
        public String description;
        public Double price;
        public Integer stockQuantity;
        public String category;
    }

    public static class StockUpdateRequest {
        public Integer quantity;
    }
}
