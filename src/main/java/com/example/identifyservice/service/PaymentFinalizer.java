package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.OrderStatus;
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

    /**
     * MoMo result codes that definitively mean the customer's payment failed or was refused/cancelled.
     * Extend it from MoMo's result-code table. Any other non-zero code (system/request errors such as 10, 11, 13,
     * 20, 40, 99, or a missing code) is not proof of failure and leaves the attempt PENDING for a later re-query.
     */
    private static final Set<Integer> TERMINAL_FAILURE_CODES =
            Set.of(1001, 1002, 1003, 1004, 1005, 1006, 1007, 1017, 1026, 1080, 1081, 4001, 4002, 4100);

    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    ApplicationEventPublisher publisher;

    @Transactional
    public FinalizeOutcome finalizePayment(String providerOrderId, int resultCode, long amount, Long transId,
                                           String raw) {
        // Locking read: concurrent finalizers of the same attempt are serialised here.
        Payment payment = paymentRepository.findForUpdateByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        Order order = payment.getOrder();
        PaymentAttemptStatus previous = payment.getStatus();

        if (IN_PROGRESS.contains(resultCode)) return FinalizeOutcome.PENDING;
        if (previous == PaymentAttemptStatus.SUCCESS) return FinalizeOutcome.ALREADY_PROCESSED;

        if (resultCode != 0) {
            if (!TERMINAL_FAILURE_CODES.contains(resultCode)) {
                log.warn("MoMo result {} ({}) for attempt {} is not a terminal payment failure; leaving it pending",
                        resultCode, raw, providerOrderId);
                return FinalizeOutcome.PENDING;
            }
            if (previous == PaymentAttemptStatus.PENDING) {
                payment.setStatus(PaymentAttemptStatus.FAILED);
                recordProviderData(payment, transId, raw);
                paymentRepository.save(payment);
            }
            return FinalizeOutcome.FAILED;
        }

        if (amount != payment.getAmount() || amount != order.getTotal()) {
            log.warn("MoMo amount mismatch for order {}: expected {} but received {} (attempt {})",
                    order.getCode(), order.getTotal(), amount, providerOrderId);
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        payment.setStatus(PaymentAttemptStatus.SUCCESS);
        recordProviderData(payment, transId, raw);
        paymentRepository.save(payment);

        String orderId = order.getId();
        String orderCode = order.getCode();
        if (orderRepository.markPaid(orderId, Instant.now()) == 1) {
            publisher.publishEvent(new OrderConfirmedEvent(orderId));
            return FinalizeOutcome.PAID;
        }

        // markPaid changed nothing: re-read with a locking current read (a plain read could return this
        // transaction's stale snapshot and misreport a concurrently paid order).
        var current = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (current.getStatus() == OrderStatus.CANCELLED) {
            log.warn("LATE PAYMENT: MoMo transId={} succeeded for order {} which is already {} - manual refund required",
                    transId, orderCode, current.getStatus());
            return FinalizeOutcome.LATE_PAYMENT_ORDER_CLOSED;
        }
        log.warn("DUPLICATE PAYMENT: MoMo transId={} paid again for order {} (paymentStatus {}) - manual refund required",
                transId, orderCode, current.getPaymentStatus());
        return FinalizeOutcome.DUPLICATE_PAYMENT;
    }

    private static void recordProviderData(Payment payment, Long transId, String raw) {
        if (transId != null) payment.setTransId(transId);
        if (raw != null) payment.setRawResponse(raw.substring(0, Math.min(raw.length(), 4000)));
    }
}
