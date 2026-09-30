package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Closes MoMo orders whose payment window elapsed. Because there is no public IPN in local development,
 * it first asks MoMo about every unresolved attempt, so a customer who paid and never returned is confirmed.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OrderExpiryService {
    OrderRepository orderRepository;
    PaymentService paymentService;
    OrderService orderService;

    @Scheduled(fixedDelayString = "${shop.expiry-job-interval-ms:60000}", initialDelayString = "${shop.expiry-job-initial-delay-ms:60000}")
    public void scheduledRun() {
        expireOverdueOrders();
    }

    /** @return number of orders cancelled in this run */
    public int expireOverdueOrders() {
        int cancelled = 0;
        for (Order order : orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, Instant.now())) {
            try {
                paymentService.reconcilePendingAttempts(order);
                if (orderService.cancelPendingPayment(order.getId(), PaymentStatus.EXPIRED)) cancelled++;
            } catch (RuntimeException e) {
                log.warn("Could not expire order {} yet (will retry): {}", order.getCode(), e.getMessage());
            }
        }
        return cancelled;
    }
}
