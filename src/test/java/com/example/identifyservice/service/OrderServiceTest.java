package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "alice", roles = "USER")
class OrderServiceTest {
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variants;
    @Autowired EntityManager em;
    @Autowired OrderRepository orderRepository;

    Product tee;
    ProductVariant m;

    @BeforeEach
    void setUp() {
        data.user("alice");
        data.user("bob");
        tee = data.product("basic-tee", 200_000, true);
        m = data.variant(tee, "M", "white", 5, null);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private CheckoutRequest request(PaymentMethod method, String province) {
        return new CheckoutRequest("Alice", "0901234567", "alice@example.com", "12 Nguyen Hue", province, "Phường 1", null, method);
    }

    private void actAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, "x", role));
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    @Test
    void codCheckoutComputesTotalsFromDatabasePricesAndReservesStock() {
        cart.addItem(m.getId(), 2);
        OrderResponse order = orders.checkout(request(PaymentMethod.COD, "Hà Nội"));

        assertThat(order.subtotal()).isEqualTo(400_000);
        assertThat(order.shippingFee()).isEqualTo(25_000);
        assertThat(order.total()).isEqualTo(425_000);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.expiresAt()).isNull();
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualTo(200_000);

        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(3);
        assertThat(cart.getCart().items()).isEmpty();
    }

    @Test
    void momoCheckoutIsPendingPaymentWithFifteenMinuteExpiry() {
        cart.addItem(m.getId(), 1);
        OrderResponse order = orders.checkout(request(PaymentMethod.MOMO, "TP Hồ Chí Minh"));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.expiresAt()).isBetween(Instant.now().plus(14, ChronoUnit.MINUTES),
                Instant.now().plus(16, ChronoUnit.MINUTES));
    }

    @Test
    void emptyCartUnknownProvinceAndInactiveProductAreRejected() {
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Hà Nội")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.CART_EMPTY);

        cart.addItem(m.getId(), 1);
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Atlantis")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_PROVINCE);

        tee.setActive(false);
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Hà Nội")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.VARIANT_NOT_FOUND);
    }

    @Test
    void stockReducedAfterAddingToCartBlocksCheckout() {
        cart.addItem(m.getId(), 5);
        m.setStock(2);
        variants.save(m);
        em.flush();
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Hà Nội")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void otherUsersOrderLooksNotFound() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();
        assertThat(orders.getMyOrder(code).code()).isEqualTo(code);
        assertThat(orders.myOrders(0, 10).items()).extracting(OrderResponse::code).contains(code);

        actAs("bob", "ROLE_USER");
        assertThatThrownBy(() -> orders.getMyOrder(code))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(orders.myOrders(0, 10).items()).extracting(OrderResponse::code).doesNotContain(code);
    }

    @Test
    void adminMovesCodOrderThroughToCompletedAndItBecomesPaid() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();

        actAs("admin", "ROLE_ADMIN");
        assertThat(orders.adminUpdateStatus(code, OrderStatus.CONFIRMED).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orders.adminUpdateStatus(code, OrderStatus.SHIPPING).status()).isEqualTo(OrderStatus.SHIPPING);
        OrderResponse done = orders.adminUpdateStatus(code, OrderStatus.COMPLETED);
        assertThat(done.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(done.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(done.paidAt()).isNotNull();
    }

    @Test
    void illegalTransitionIsRejected() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();
        actAs("admin", "ROLE_ADMIN");
        assertThatThrownBy(() -> orders.adminUpdateStatus(code, OrderStatus.COMPLETED))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void adminCancelRestocks() {
        cart.addItem(m.getId(), 2);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();
        actAs("admin", "ROLE_ADMIN");
        orders.adminUpdateStatus(code, OrderStatus.CANCELLED);
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void cancelPendingPaymentRestocksOnlyOnce() {
        cart.addItem(m.getId(), 2);
        var order = orders.checkout(request(PaymentMethod.MOMO, "Hà Nội"));
        String orderId = orders.requireOwnedOrder(order.code()).getId();

        assertThat(orders.cancelPendingPayment(orderId, PaymentStatus.EXPIRED)).isTrue();
        assertThat(orders.cancelPendingPayment(orderId, PaymentStatus.EXPIRED)).isFalse();
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(orders.getMyOrder(order.code()).paymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void adminCancelOfPendingPaymentRestocksOnceKeepsUnpaidAndBlocksLatePayment() {
        cart.addItem(m.getId(), 2);
        String code = orders.checkout(request(PaymentMethod.MOMO, "Hà Nội")).code();
        String orderId = orders.requireOwnedOrder(code).getId();

        actAs("admin", "ROLE_ADMIN");
        OrderResponse cancelled = orders.adminUpdateStatus(code, OrderStatus.CANCELLED);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThatThrownBy(() -> orders.adminUpdateStatus(code, OrderStatus.CANCELLED))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_ORDER_STATUS);

        assertThat(orderRepository.markPaid(orderId, Instant.now())).isZero();
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void markPaidMovesPendingPaymentOrderToPendingConfirmOnce() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.MOMO, "Hà Nội")).code();
        String orderId = orders.requireOwnedOrder(code).getId();

        assertThat(orderRepository.markPaid(orderId, Instant.now())).isEqualTo(1);
        assertThat(orderRepository.markPaid(orderId, Instant.now())).isZero();
        var paid = orderRepository.findById(orderId).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(orders.cancelPendingPayment(orderId, PaymentStatus.EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void nonAdminCannotUseAdminOperations() {
        assertThatThrownBy(() -> orders.adminList(null, 0, 10))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void checkoutSnapshotsProductCostIntoOrderItem() {
        tee.setCostPrice(80_000L);
        cart.addItem(m.getId(), 1);
        OrderResponse order = orders.checkout(request(PaymentMethod.COD, "Hà Nội"));

        tee.setCostPrice(120_000L); // later cost change must not alter the snapshot
        em.flush();
        em.clear();
        var stored = orderRepository.findByCode(order.code()).orElseThrow();
        assertThat(stored.getItems()).hasSize(1);
        assertThat(stored.getItems().get(0).getUnitCost()).isEqualTo(80_000L);
    }

    @Test
    void checkoutLeavesUnitCostNullWhenProductHasNoCost() {
        cart.addItem(m.getId(), 1);
        OrderResponse order = orders.checkout(request(PaymentMethod.COD, "Hà Nội"));
        em.flush();
        em.clear();
        assertThat(orderRepository.findByCode(order.code()).orElseThrow().getItems().get(0).getUnitCost()).isNull();
    }

    @Test
    void checkoutStoresTrimmedWardAndReturnsIt() {
        cart.addItem(m.getId(), 1);
        OrderResponse order = orders.checkout(new CheckoutRequest("Alice", "0901234567", "alice@example.com",
                "12 Nguyen Hue", "Hà Nội", "  Phường Bến Nghé ", null, PaymentMethod.COD));
        assertThat(order.ward()).isEqualTo("Phường Bến Nghé");
        assertThat(orders.getMyOrder(order.code()).ward()).isEqualTo("Phường Bến Nghé");
    }
}
