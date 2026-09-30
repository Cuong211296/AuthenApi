package com.example.identifyservice.event;

import com.example.identifyservice.service.OrderMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the confirmation email after the order transaction commits; a mail failure never affects the order. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMailListener {
    private final OrderMailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(OrderConfirmedEvent event) {
        try {
            mailService.sendOrderConfirmation(event.orderId());
        } catch (Exception e) {
            log.error("Could not send confirmation email for order {} ({})", event.orderId(), e.getClass().getName());
        }
    }
}
