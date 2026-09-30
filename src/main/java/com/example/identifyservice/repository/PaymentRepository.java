package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByProviderOrderId(String providerOrderId);
    List<Payment> findByOrderAndStatus(Order order, PaymentAttemptStatus status);
    List<Payment> findByOrder(Order order);
}
