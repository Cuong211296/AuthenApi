package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Deliberately not @Transactional: real concurrent transactions against committed data. */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {
    static class EventCollector {
        final List<String> orderIds = new CopyOnWriteArrayList<>();

        @EventListener
        void on(OrderConfirmedEvent event) {
            orderIds.add(event.orderId());
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        EventCollector eventCollector() {
            return new EventCollector();
        }
    }

    @Autowired TestDataFactory data;
    @Autowired PaymentFinalizer finalizer;
    @Autowired OrderRepository orders;
    @Autowired EventCollector collector;

    @Test
    void simultaneousReturnAndIpnConfirmExactlyOnceWithoutFalseLateAlert() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        var user = data.user("racer-" + tag);
        var variant = data.variant(data.product("race-" + tag, 100_000, true), "M", "red", 5, null);
        Order order = data.pendingMomoOrder(user, variant, 1, Instant.now().plus(15, ChronoUnit.MINUTES));
        String providerOrderId = order.getCode() + "_" + tag;
        data.attempt(order, providerOrderId);
        long total = order.getTotal();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<FinalizeOutcome> call = () -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                return finalizer.finalizePayment(providerOrderId, 0, total, 555L, "{}");
            };
            List<Future<FinalizeOutcome>> futures = new ArrayList<>();
            futures.add(pool.submit(call));
            futures.add(pool.submit(call));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<FinalizeOutcome> outcomes = new ArrayList<>();
            for (Future<FinalizeOutcome> f : futures) outcomes.add(f.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(FinalizeOutcome.PAID, FinalizeOutcome.ALREADY_PROCESSED);
        } finally {
            pool.shutdownNow();
        }

        assertThat(collector.orderIds.stream().filter(order.getId()::equals).count()).isEqualTo(1);
        Order stored = orders.findById(order.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(stored.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
