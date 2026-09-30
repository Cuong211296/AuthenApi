package com.example.identifyservice.service;

import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.dto.response.PaymentResultResponse;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.momo.MomoIpnRequest;
import com.example.identifyservice.momo.MomoProperties;
import com.example.identifyservice.momo.MomoSigner;
import com.example.identifyservice.repository.PaymentRepository;
import com.example.identifyservice.testsupport.FakeMomoGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@WithMockUser(username = "alice", roles = "USER")
class PaymentServiceTest {
    @Autowired PaymentService payments;
    @Autowired TestDataFactory data;
    @Autowired FakeMomoGateway momo;
    @Autowired MomoProperties momoProps;
    @Autowired ShopProperties shopProps;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderService orders;
    @Autowired EntityManager em;
    @Autowired ApplicationEvents events;
    @Autowired PaymentFinalizer finalizer;

    User alice;
    Order order;

    @BeforeEach
    void setUp() {
        momo.reset();
        alice = data.user("alice");
        var product = data.product("pay-tee", 100_000, true);
        ProductVariant variant = data.variant(product, "M", "red", 10, null);
        order = data.pendingMomoOrder(alice, variant, 2, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    private long confirmedEvents() {
        return events.stream(OrderConfirmedEvent.class).count();
    }

    @Test
    void startPaymentReturnsPayUrlAndRecordsAttemptWithRedirectToFrontend() {
        var response = payments.startMomoPayment(order.getCode());

        assertThat(response.payUrl()).isEqualTo("https://pay.example/checkout");
        assertThat(momo.creates).hasSize(1);
        var cmd = momo.creates.get(0);
        assertThat(cmd.amount()).isEqualTo(order.getTotal());
        assertThat(cmd.redirectUrl()).isEqualTo(shopProps.frontendUrl() + "/payment/result?orderCode=" + order.getCode());
        assertThat(cmd.ipnUrl()).isEqualTo(momoProps.ipnUrl());
        assertThat(paymentRepository.findByProviderOrderId(cmd.providerOrderId())).isPresent();
        assertThat(cmd.providerOrderId()).startsWith(order.getCode());
    }

    @Test
    void gatewayRefusalIsReportedAndNoAttemptIsStored() {
        momo.createResult = new com.example.identifyservice.momo.MomoCreateResult(99, "bad", "");
        assertThatThrownBy(() -> payments.startMomoPayment(order.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.PAYMENT_GATEWAY_ERROR);
        assertThat(paymentRepository.findByOrder(order)).isEmpty();
    }

    @Test
    void codOrExpiredOrOthersOrdersCannotBePaid() {
        Order cod = data.pendingMomoOrder(alice, data.variant(data.product("cod-tee", 1000, true), "S", "x", 1, null),
                1, null);
        cod.setPaymentMethod(PaymentMethod.COD);
        assertThatThrownBy(() -> payments.startMomoPayment(cod.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE);

        Order expired = data.pendingMomoOrder(alice, data.variant(data.product("old-tee", 1000, true), "S", "x", 1, null),
                1, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> payments.startMomoPayment(expired.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE);

        data.user("bob");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "x", "ROLE_USER"));
        assertThatThrownBy(() -> payments.startMomoPayment(order.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void returnMarksOrderPaidAndSecondReturnDoesNothingMore() {
        payments.startMomoPayment(order.getCode());
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());

        PaymentResultResponse first = payments.confirmMomoReturn(order.getCode());
        PaymentResultResponse second = payments.confirmMomoReturn(order.getCode());

        assertThat(first.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(first.orderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(second).isEqualTo(first);
        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void returnThenIpnForSamePaymentConfirmsOnce() {
        payments.startMomoPayment(order.getCode());
        String providerOrderId = momo.creates.get(0).providerOrderId();
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());
        payments.confirmMomoReturn(order.getCode());

        payments.handleIpn(signedIpn(providerOrderId, order.getTotal(), 0));

        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void amountMismatchDoesNotMarkPaid() {
        payments.startMomoPayment(order.getCode());
        momo.queryHandler = id -> FakeMomoGateway.paid(1_000);

        assertThatThrownBy(() -> payments.confirmMomoReturn(order.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        em.flush();
        em.clear();
        assertThat(orders.getMyOrder(order.getCode()).paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(confirmedEvents()).isZero();
    }

    @Test
    void stillPendingChangesNothing() {
        payments.startMomoPayment(order.getCode());
        var result = payments.confirmMomoReturn(order.getCode());
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void failedAttemptKeepsOrderPayableSoCustomerCanRetry() {
        payments.startMomoPayment(order.getCode());
        String firstAttempt = momo.creates.get(0).providerOrderId();
        momo.queryHandler = id -> new com.example.identifyservice.momo.MomoQueryResult(1006, "denied", -1, null, "{}");

        var result = payments.confirmMomoReturn(order.getCode());

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findByProviderOrderId(firstAttempt).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.FAILED);
        payments.startMomoPayment(order.getCode());
        assertThat(momo.creates).hasSize(2);
        assertThat(momo.creates.get(1).providerOrderId()).isNotEqualTo(firstAttempt);
    }

    @Test
    void paymentArrivingAfterCancellationDoesNotResurrectTheOrder() {
        payments.startMomoPayment(order.getCode());
        assertThat(orders.cancelPendingPayment(order.getId(), PaymentStatus.EXPIRED)).isTrue();
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());

        var result = payments.confirmMomoReturn(order.getCode());

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(confirmedEvents()).isZero();
    }

    @Test
    void ipnWithBadSignatureIsRejectedAndValidOneIsAccepted() {
        payments.startMomoPayment(order.getCode());
        String providerOrderId = momo.creates.get(0).providerOrderId();

        MomoIpnRequest forged = new MomoIpnRequest("PARTNER", providerOrderId, "r", order.getTotal(), "i", "t", 1, 0,
                "ok", "qr", 1L, "", "deadbeef");
        assertThatThrownBy(() -> payments.handleIpn(forged))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_PAYMENT_SIGNATURE);
        assertThat(confirmedEvents()).isZero();

        payments.handleIpn(signedIpn(providerOrderId, order.getTotal(), 0));
        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void nonTerminalCodesLeaveAttemptPendingAndTerminalCodeFails() {
        payments.startMomoPayment(order.getCode());
        String attempt = momo.creates.get(0).providerOrderId();

        for (int code : new int[]{99, 40, -1}) {
            assertThat(finalizer.finalizePayment(attempt, code, -1, null, "{}")).isEqualTo(FinalizeOutcome.PENDING);
            assertThat(paymentRepository.findByProviderOrderId(attempt).orElseThrow().getStatus())
                    .isEqualTo(PaymentAttemptStatus.PENDING);
        }
        assertThat(finalizer.finalizePayment(attempt, 1006, -1, null, "{}")).isEqualTo(FinalizeOutcome.FAILED);
        assertThat(paymentRepository.findByProviderOrderId(attempt).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.FAILED);
    }

    @Test
    void alreadySuccessfulAttemptReportsAlreadyProcessedEvenForInProgressCode() {
        payments.startMomoPayment(order.getCode());
        String attempt = momo.creates.get(0).providerOrderId();
        assertThat(finalizer.finalizePayment(attempt, 0, order.getTotal(), 42L, "{}")).isEqualTo(FinalizeOutcome.PAID);

        assertThat(finalizer.finalizePayment(attempt, 7000, -1, null, "{}")).isEqualTo(FinalizeOutcome.ALREADY_PROCESSED);
    }

    @Test
    void successAfterNonTerminalCodeMarksOrderPaid() {
        payments.startMomoPayment(order.getCode());
        String attempt = momo.creates.get(0).providerOrderId();
        assertThat(finalizer.finalizePayment(attempt, 99, -1, null, "{}")).isEqualTo(FinalizeOutcome.PENDING);

        assertThat(finalizer.finalizePayment(attempt, 0, order.getTotal(), 42L, "{}")).isEqualTo(FinalizeOutcome.PAID);
        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void secondAttemptPaidOnAlreadyPaidOrderIsFlaggedAsDuplicate() {
        payments.startMomoPayment(order.getCode());
        payments.startMomoPayment(order.getCode());
        String first = momo.creates.get(0).providerOrderId();
        String second = momo.creates.get(1).providerOrderId();
        assertThat(second).isNotEqualTo(first).startsWith(order.getCode() + "_").hasSizeLessThanOrEqualTo(50);

        assertThat(finalizer.finalizePayment(first, 0, order.getTotal(), 1L, "{}")).isEqualTo(FinalizeOutcome.PAID);
        assertThat(finalizer.finalizePayment(second, 0, order.getTotal(), 2L, "{}"))
                .isEqualTo(FinalizeOutcome.DUPLICATE_PAYMENT);
        assertThat(finalizer.finalizePayment(first, 0, order.getTotal(), 9L, "{}"))
                .isEqualTo(FinalizeOutcome.ALREADY_PROCESSED);
        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void finalAttemptKeepsItsOriginalTransIdAndRawResponse() {
        payments.startMomoPayment(order.getCode());
        String attempt = momo.creates.get(0).providerOrderId();
        finalizer.finalizePayment(attempt, 0, order.getTotal(), 1L, "first");

        finalizer.finalizePayment(attempt, 0, order.getTotal(), 9L, null);

        var stored = paymentRepository.findByProviderOrderId(attempt).orElseThrow();
        assertThat(stored.getTransId()).isEqualTo(1L);
        assertThat(stored.getRawResponse()).isEqualTo("first");
    }

    @Test
    void ipnWithBlankMomoKeysIsAGatewayErrorNotACrash() {
        var unconfigured = new PaymentService(null, null, null, null,
                new MomoProperties("https://x", "", "", "", "payWithMethod", ""), null);
        var ipn = new MomoIpnRequest("PC", "o", "r", 1, "i", "t", 1, 0, "ok", "qr", 1L, "", "sig");
        assertThatThrownBy(() -> unconfigured.handleIpn(ipn))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    private MomoIpnRequest signedIpn(String providerOrderId, long amount, int resultCode) {
        MomoIpnRequest unsigned = new MomoIpnRequest("PARTNER", providerOrderId, "req-1", amount, "info",
                "momo_wallet", 777, resultCode, "Successful.", "qr", 1700000000000L, "", null);
        String signature = MomoSigner.hmacSha256Hex(momoProps.secretKey(),
                MomoSigner.ipnRaw(momoProps.accessKey(), unsigned));
        return new MomoIpnRequest(unsigned.partnerCode(), unsigned.orderId(), unsigned.requestId(), unsigned.amount(),
                unsigned.orderInfo(), unsigned.orderType(), unsigned.transId(), unsigned.resultCode(),
                unsigned.message(), unsigned.payType(), unsigned.responseTime(), unsigned.extraData(), signature);
    }
}
