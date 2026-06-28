package com.example.failureapp.service;

import com.example.failureapp.model.Order;
import com.example.failureapp.model.OrderItem;
import com.example.failureapp.model.Payment;
import com.example.failureapp.model.Product;
import com.example.failureapp.model.User;
import com.example.failureapp.model.Order.OrderStatus;
import com.example.failureapp.model.Payment.PaymentMethod;
import com.example.failureapp.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    @Transactional
    public Order createOrder(Long userId, List<OrderItemRequest> items) {
        long startTime = System.currentTimeMillis();
        log.info("Creating order for user: {}, items: {}", userId, items.size());

        User user = userService.getUserById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        double totalAmount = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemRequest : items) {
            Product product = inventoryService.getProductById(itemRequest.productId)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemRequest.productId));

            // Reserve stock (this can cause race conditions and optimistic locking failures)
            inventoryService.reserveStock(itemRequest.productId, itemRequest.quantity);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.quantity)
                    .priceAtPurchase(product.getPrice())
                    .subtotal(product.getPrice() * itemRequest.quantity)
                    .build();

            orderItems.add(orderItem);
            totalAmount += orderItem.getSubtotal();
        }

        order.setItems(orderItems);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PROCESSING);

        order = orderRepository.save(order);

        long processingTime = System.currentTimeMillis() - startTime;
        order.setProcessingTimeMs(processingTime);

        log.info("Order created successfully: orderNumber={}, totalAmount={}, processingTime={}ms",
                order.getOrderNumber(), order.getTotalAmount(), processingTime);

        return orderRepository.save(order);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED)
    public Order processOrderPayment(Long orderId, PaymentMethod paymentMethod) {
        log.info("Processing payment for order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new RuntimeException("Order is not in PROCESSING state: " + order.getStatus());
        }

        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order = orderRepository.save(order);

        try {
            Payment payment = paymentService.processPayment(order, paymentMethod);

            if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                order.setStatus(OrderStatus.CONFIRMED);
                log.info("Order payment confirmed: orderNumber={}", order.getOrderNumber());
            } else {
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                // Release reserved stock
                for (OrderItem item : order.getItems()) {
                    inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
                }
                log.error("Order payment failed: orderNumber={}", order.getOrderNumber());
            }
        } catch (Exception e) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            log.error("Exception during payment processing for order {}: {}", order.getOrderNumber(), e.getMessage());

            // Release reserved stock on payment failure
            for (OrderItem item : order.getItems()) {
                try {
                    inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
                } catch (Exception releaseException) {
                    log.error("Failed to release stock for product {}: {}",
                            item.getProduct().getId(), releaseException.getMessage());
                }
            }
            throw e;
        }

        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new RuntimeException("Cannot cancel shipped or delivered orders");
        }

        // Release stock
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
        }

        // Refund payment if completed
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            Optional<Payment> payment = paymentService.getPaymentByOrderId(orderId);
            payment.ifPresent(p -> paymentService.refundPayment(p.getTransactionId()));
        }

        order.setStatus(OrderStatus.CANCELLED);
        log.info("Order cancelled: orderNumber={}", order.getOrderNumber());

        return orderRepository.save(order);
    }

    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    public Optional<Order> getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByCreatedAtBetween(startDate, endDate);
    }

    // Inner class for order item request
    public static class OrderItemRequest {
        public Long productId;
        public Integer quantity;

        public OrderItemRequest() {}

        public OrderItemRequest(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}
