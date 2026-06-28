package com.example.failureapp.repository;

import com.example.failureapp.model.Payment;
import com.example.failureapp.model.Payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.retryCount > :count AND p.status = :status")
    List<Payment> findFailedPaymentsWithRetries(@Param("count") Integer count, @Param("status") PaymentStatus status);
}
