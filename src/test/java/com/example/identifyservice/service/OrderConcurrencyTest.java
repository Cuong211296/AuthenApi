package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {
    @Autowired TestDataFactory data;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired ProductVariantRepository variants;
    @Autowired OrderRepository orders;

    private CheckoutRequest request() {
        return new CheckoutRequest("Racer", "0901234567", "racer@example.com", "1 Race St", "Hà Nội", "Phường 1", null,
                PaymentMethod.COD);
    }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void adminLogin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Callable<String> checkoutAs(String username, String variantId, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            login(username);
            try {
                cartService.addItem(variantId, 1);
                ready.countDown();
                go.await();
                orderService.checkout(request());
                return "OK";
            } catch (AppException e) {
                return e.getErrorCode().name();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    @Test
    void lastUnitIsSoldExactlyOnce() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Product p = data.product("race-" + tag, 100_000, true);
        ProductVariant v = data.variant(p, "M", "black", 1, null);
        User u1 = data.user("racer1-" + tag);
        User u2 = data.user("racer2-" + tag);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<String> f1 = pool.submit(checkoutAs(u1.getUsername(), v.getId(), ready, go));
            Future<String> f2 = pool.submit(checkoutAs(u2.getUsername(), v.getId(), ready, go));
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<String> results = new ArrayList<>(List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS)));
            assertThat(results).containsExactlyInAnyOrder("OK", "OUT_OF_STOCK");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertThat(variants.findById(v.getId()).orElseThrow().getStock()).isZero();
    }

    @Test
    void concurrentAdminCancelRestocksExactlyOnce() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Product p = data.product("cancel-" + tag, 100_000, true);
        ProductVariant v = data.variant(p, "M", "black", 5, null);
        User u = data.user("canceller-" + tag);

        login(u.getUsername());
        String code;
        try {
            cartService.addItem(v.getId(), 2);
            code = orderService.checkout(request()).code();
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(variants.findById(v.getId()).orElseThrow().getStock()).isEqualTo(3);
        adminLogin();
        try {
            orderService.adminUpdateStatus(code, OrderStatus.CONFIRMED);
        } finally {
            SecurityContextHolder.clearContext();
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<String> cancel = () -> {
                adminLogin();
                try {
                    ready.countDown();
                    go.await();
                    orderService.adminUpdateStatus(code, OrderStatus.CANCELLED);
                    return "OK";
                } catch (AppException e) {
                    return e.getErrorCode().name();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Future<String> f1 = pool.submit(cancel);
            Future<String> f2 = pool.submit(cancel);
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<String> results = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("OK", "INVALID_ORDER_STATUS");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertThat(variants.findById(v.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void failureOnSecondLineRollsBackFirstLineReservation() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Product p = data.product("roll-" + tag, 100_000, true);
        ProductVariant first = data.variant(p, "M", "black", 5, null);
        ProductVariant second = data.variant(p, "L", "black", 5, null);
        User user = data.user("roller-" + tag);

        login(user.getUsername());
        try {
            cartService.addItem(first.getId(), 2);
            cartService.addItem(second.getId(), 5);
            second.setStock(1);            // stock shrinks after the item was added to the cart
            variants.save(second);

            assertThatThrownBy(() -> orderService.checkout(request())).isInstanceOf(AppException.class);

            assertThat(variants.findById(first.getId()).orElseThrow().getStock()).isEqualTo(5);
            assertThat(variants.findById(second.getId()).orElseThrow().getStock()).isEqualTo(1);
            assertThat(cartService.getCart().items()).hasSize(2);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
