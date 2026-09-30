package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.FakeMomoGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderExpiryServiceTest {
    @Autowired OrderExpiryService expiry;
    @Autowired TestDataFactory data;
    @Autowired FakeMomoGateway momo;
    @Autowired OrderRepository orders;
    @Autowired ProductVariantRepository variants;
    @Autowired TransactionTemplate tx;

    User user;
    ProductVariant variant;

    @BeforeEach
    void setUp() {
        momo.reset();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        user = data.user("exp-" + tag);
        variant = data.variant(data.product("exp-" + tag, 100_000, true), "M", "red", 3, null);
    }

    private Order overdueOrder(int qty) {
        // stock held by this order: simulate the reservation the checkout would have made
        tx.executeWithoutResult(s -> variants.decrementStock(variant.getId(), qty));
        return data.pendingMomoOrder(user, variant, qty, Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    private Order reload(Order o) {
        return orders.findById(o.getId()).orElseThrow();
    }

    @Test
    void overdueUnpaidOrderIsCancelledAndStockReturned() {
        Order order = overdueOrder(2);
        data.attempt(order, order.getCode() + "_1");

        expiry.expireOverdueOrders();

        Order after = reload(order);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    @Test
    void notYetOverdueOrderIsLeftAlone() {
        tx.executeWithoutResult(s -> variants.decrementStock(variant.getId(), 1));
        Order order = data.pendingMomoOrder(user, variant, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        expiry.expireOverdueOrders();
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void customerWhoPaidButNeverReturnedIsConfirmedNotCancelled() {
        Order order = overdueOrder(1);
        String providerOrderId = order.getCode() + "_1";
        data.attempt(order, providerOrderId);
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());

        expiry.expireOverdueOrders();

        Order after = reload(order);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(after.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void momoOutageDoesNotCancelAnOrderThatMightBePaid() {
        Order order = overdueOrder(1);
        data.attempt(order, order.getCode() + "_1");
        momo.queryFailure = new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);

        expiry.expireOverdueOrders();

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void oneBrokenOrderDoesNotStopTheRest() {
        Order broken = overdueOrder(1);
        data.attempt(broken, broken.getCode() + "_1");
        Order fine = overdueOrder(1);
        momo.queryHandler = id -> {
            if (id.startsWith(broken.getCode())) throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            return FakeMomoGateway.pending();
        };

        expiry.expireOverdueOrders();

        assertThat(reload(broken).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(reload(fine).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
