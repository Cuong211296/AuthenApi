package com.example.identifyservice.service;

import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.dto.response.MomoPayResponse;
import com.example.identifyservice.dto.response.PaymentResultResponse;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.momo.MomoCreateCommand;
import com.example.identifyservice.momo.MomoGateway;
import com.example.identifyservice.momo.MomoIpnRequest;
import com.example.identifyservice.momo.MomoProperties;
import com.example.identifyservice.momo.MomoQueryResult;
import com.example.identifyservice.momo.MomoSigner;
import com.example.identifyservice.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Deliberately not @Transactional: it makes HTTP calls to MoMo and must not hold a DB transaction meanwhile. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {
    OrderService orderService;
    PaymentRepository paymentRepository;
    PaymentFinalizer finalizer;
    MomoGateway gateway;
    MomoProperties momoProperties;
    ShopProperties shopProperties;

    public MomoPayResponse startMomoPayment(String orderCode) {
        Order order = orderService.requireOwnedOrder(orderCode);
        boolean payable = order.getPaymentMethod() == PaymentMethod.MOMO
                && order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getExpiresAt() != null && order.getExpiresAt().isAfter(Instant.now());
        if (!payable) throw new AppException(ErrorCode.ORDER_NOT_PAYABLE);

        String providerOrderId = order.getCode() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String requestId = UUID.randomUUID().toString();
        var result = gateway.create(new MomoCreateCommand(providerOrderId, requestId, order.getTotal(),
                "Thanh toan don hang " + order.getCode(),
                shopProperties.frontendUrl() + "/payment/result?orderCode=" + order.getCode(),
                momoProperties.ipnUrl()));
        if (result.resultCode() != 0 || result.payUrl() == null || result.payUrl().isBlank())
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);

        paymentRepository.save(Payment.builder().order(order).requestId(requestId).providerOrderId(providerOrderId)
                .amount(order.getTotal()).status(PaymentAttemptStatus.PENDING).build());
        return new MomoPayResponse(result.payUrl());
    }

    /** Called when the customer's browser comes back from MoMo. MoMo's redirect parameters are ignored. */
    public PaymentResultResponse confirmMomoReturn(String orderCode) {
        Order order = orderService.requireOwnedOrder(orderCode);
        if (order.getPaymentMethod() != PaymentMethod.MOMO) throw new AppException(ErrorCode.ORDER_NOT_PAYABLE);
        if (order.getPaymentStatus() != PaymentStatus.PAID) reconcilePendingAttempts(order);
        Order fresh = orderService.requireOwnedOrder(orderCode);
        return new PaymentResultResponse(fresh.getCode(), fresh.getStatus(), fresh.getPaymentStatus());
    }

    /** Asks MoMo for the truth about every unresolved attempt of the order and applies it. */
    public void reconcilePendingAttempts(Order order) {
        for (Payment attempt : paymentRepository.findByOrderAndStatus(order, PaymentAttemptStatus.PENDING)) {
            MomoQueryResult q = gateway.query(attempt.getProviderOrderId(), attempt.getRequestId());
            finalizer.finalizePayment(attempt.getProviderOrderId(), q.resultCode(), q.amount(), q.transId(), q.raw());
        }
    }

    public void handleIpn(MomoIpnRequest ipn) {
        if (isBlank(momoProperties.secretKey()) || isBlank(momoProperties.accessKey()))
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        String expected = MomoSigner.hmacSha256Hex(momoProperties.secretKey(),
                MomoSigner.ipnRaw(momoProperties.accessKey(), ipn));
        if (!MomoSigner.constantTimeEquals(expected, ipn.signature()))
            throw new AppException(ErrorCode.INVALID_PAYMENT_SIGNATURE);
        finalizer.finalizePayment(ipn.orderId(), ipn.resultCode(), ipn.amount(), ipn.transId(), null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
