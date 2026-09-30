package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByProviderOrderId(String providerOrderId);
    /** Locking current read: serialises concurrent finalizers of the same attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerOrderId = :providerOrderId")
    Optional<Payment> findForUpdateByProviderOrderId(@Param("providerOrderId") String providerOrderId);

    List<Payment> findByOrderAndStatus(Order order, PaymentAttemptStatus status);
    List<Payment> findByOrder(Order order);
}
