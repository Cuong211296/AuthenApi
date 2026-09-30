package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/** The single place where a MoMo result changes an order. Idempotent and safe under concurrent calls. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentFinalizer {
    /** MoMo result codes meaning "still in progress" (initiated, processing, authorized). */
    private static final Set<Integer> IN_PROGRESS = Set.of(1000, 7000, 7002, 9000);

    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    ApplicationEventPublisher publisher;

    @Transactional
    public FinalizeOutcome finalizePayment(String providerOrderId, int resultCode, long amount, Long transId,
                                           String raw) {
        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        Order order = payment.getOrder();

        if (IN_PROGRESS.contains(resultCode)) return FinalizeOutcome.PENDING;

        payment.setTransId(transId);
        payment.setRawResponse(raw == null ? null : raw.substring(0, Math.min(raw.length(), 4000)));

        if (resultCode != 0) {
            if (payment.getStatus() == PaymentAttemptStatus.PENDING) payment.setStatus(PaymentAttemptStatus.FAILED);
            paymentRepository.save(payment);
            return FinalizeOutcome.FAILED;
        }

        if (amount != payment.getAmount() || amount != order.getTotal())
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        payment.setStatus(PaymentAttemptStatus.SUCCESS);
        paymentRepository.save(payment);

        String orderId = order.getId();
        String orderCode = order.getCode();
        if (orderRepository.markPaid(orderId, Instant.now()) == 1) {
            publisher.publishEvent(new OrderConfirmedEvent(orderId));
            return FinalizeOutcome.PAID;
        }

        var current = orderRepository.findById(orderId).orElseThrow();
        if (current.getPaymentStatus() == PaymentStatus.PAID) return FinalizeOutcome.ALREADY_PROCESSED;
        log.warn("LATE PAYMENT: MoMo transId={} succeeded for order {} which is already {} - manual refund required",
                transId, orderCode, current.getStatus());
        return FinalizeOutcome.LATE_PAYMENT_ORDER_CLOSED;
    }
}
