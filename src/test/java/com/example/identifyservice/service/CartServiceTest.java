package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.CartResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "alice", roles = "USER")
class CartServiceTest {
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductRepository products;

    Product tee;
    ProductVariant m;
    ProductVariant l;

    @BeforeEach
    void setUp() {
        data.user("alice");
        tee = data.product("cart-tee", 200_000, true);
        m = data.variant(tee, "M", "white", 5, null);
        l = data.variant(tee, "L", "white", 2, 220_000L);
    }

    private void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    void addingSameVariantTwiceMergesQuantity() {
        cart.addItem(m.getId(), 1);
        CartResponse r = cart.addItem(m.getId(), 2);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).quantity()).isEqualTo(3);
        assertThat(r.subtotal()).isEqualTo(600_000);
    }

    @Test
    void subtotalUsesVariantPriceOverride() {
        cart.addItem(m.getId(), 1);
        CartResponse r = cart.addItem(l.getId(), 1);
        assertThat(r.subtotal()).isEqualTo(200_000 + 220_000);
        assertThat(r.totalQuantity()).isEqualTo(2);
    }

    @Test
    void rejectsBadQuantities() {
        for (int q : new int[]{0, -1, 100}) {
            assertError(() -> cart.addItem(m.getId(), q), ErrorCode.INVALID_QUANTITY);
        }
        assertError(() -> cart.addItem(l.getId(), 3), ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void mergedQuantityCannotExceedStock() {
        cart.addItem(l.getId(), 2);
        assertError(() -> cart.addItem(l.getId(), 1), ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void inactiveVariantOrProductCannotBeAdded() {
        m.setActive(false);
        assertError(() -> cart.addItem(m.getId(), 1), ErrorCode.VARIANT_NOT_FOUND);
        tee.setActive(false);
        assertError(() -> cart.addItem(l.getId(), 1), ErrorCode.VARIANT_NOT_FOUND);
        assertError(() -> cart.addItem("no-such-variant", 1), ErrorCode.VARIANT_NOT_FOUND);
    }

    @Test
    void updateRemoveAndClear() {
        cart.addItem(m.getId(), 1);
        cart.addItem(l.getId(), 1);
        assertThat(cart.updateItem(m.getId(), 4).items())
                .filteredOn(i -> i.variantId().equals(m.getId())).extracting(i -> i.quantity()).containsExactly(4);
        assertThat(cart.removeItem(l.getId()).items()).hasSize(1);
        assertError(() -> cart.updateItem(l.getId(), 1), ErrorCode.VARIANT_NOT_FOUND);
        assertThat(cart.clear().items()).isEmpty();
    }

    @Test
    void cartsAreIsolatedPerUser() {
        cart.addItem(m.getId(), 1);
        data.user("bob");
        var bob = new org.springframework.security.authentication.TestingAuthenticationToken("bob", "x", "ROLE_USER");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(bob);
        assertThat(cart.getCart().items()).isEmpty();
    }
}
