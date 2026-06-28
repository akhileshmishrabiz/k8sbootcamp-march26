package com.example.failureapp.service;

import com.example.failureapp.model.Order;
import com.example.failureapp.model.Payment;
import com.example.failureapp.model.Payment.PaymentMethod;
import com.example.failureapp.model.Payment.PaymentStatus;
import com.example.failureapp.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Random random = new Random();

    @Transactional
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentRetry")
    public Payment processPayment(Order order, PaymentMethod method) {
        log.info("Processing payment for order: {}, amount: {}, method: {}",
                order.getOrderNumber(), order.getTotalAmount(), method);

        Payment payment = Payment.builder()
                .transactionId(UUID.randomUUID().toString())
                .order(order)
                .amount(order.getTotalAmount())
                .method(method)
                .status(PaymentStatus.PROCESSING)
                .build();

        payment = paymentRepository.save(payment);

        // Simulate calling external payment gateway
        boolean paymentSuccessful = callExternalPaymentGateway(payment);

        if (paymentSuccessful) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            payment.setGatewayResponse("SUCCESS: Payment processed successfully");
            log.info("Payment successful: transactionId={}, orderId={}",
                    payment.getTransactionId(), order.getOrderNumber());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setRetryCount(payment.getRetryCount() + 1);
            payment.setGatewayResponse("FAILED: Payment gateway declined the transaction");
            log.error("Payment failed: transactionId={}, orderId={}, retryCount={}",
                    payment.getTransactionId(), order.getOrderNumber(), payment.getRetryCount());
            throw new RuntimeException("Payment processing failed");
        }

        return paymentRepository.save(payment);
    }

    private boolean callExternalPaymentGateway(Payment payment) {
        log.debug("Calling external payment gateway: transactionId={}", payment.getTransactionId());

        // Simulate network call to external payment gateway
        try {
            Thread.sleep(200 + random.nextInt(300)); // 200-500ms latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 85% success rate
        return random.nextDouble() < 0.85;
    }

    private Payment paymentFallback(Order order, PaymentMethod method, Exception e) {
        log.error("Payment circuit breaker activated - using fallback. Order: {}, Error: {}",
                order.getOrderNumber(), e.getMessage());

        Payment payment = Payment.builder()
                .transactionId("FALLBACK-" + UUID.randomUUID().toString())
                .order(order)
                .amount(order.getTotalAmount())
                .method(method)
                .status(PaymentStatus.FAILED)
                .gatewayResponse("CIRCUIT_BREAKER_OPEN: Payment service unavailable, please try again later")
                .build();

        return paymentRepository.save(payment);
    }

    public Optional<Payment> getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId);
    }

    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional
    public Payment refundPayment(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + transactionId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Can only refund completed payments");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setGatewayResponse("REFUNDED: Payment refunded successfully");
        log.info("Payment refunded: transactionId={}", transactionId);

        return paymentRepository.save(payment);
    }
}
