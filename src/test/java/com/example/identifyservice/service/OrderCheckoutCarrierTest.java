package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCheckoutCarrierTest {
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variants;
    @Autowired EntityManager em;
    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    ProductVariant m;

    @BeforeEach
    void setUp() {
        resetFakes();
        ghn.useSampleData();
        ghn.returnFee(37_000);
        ghtk.returnFee(31_000);
        data.user("carol");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("carol", "x", "ROLE_USER"));
        m = data.variant(data.product("carrier-tee", 200_000, true), "M", "white", 5, null);
        cart.addItem(m.getId(), 1);
    }

    @AfterEach
    void tearDown() {
        resetFakes();
        SecurityContextHolder.clearContext();
    }

    private void resetFakes() {
        ghn.reset();
        ghtk.reset();
        quoteService.clearCache();
    }

    private CheckoutRequest request(String carrier) {
        return new CheckoutRequest("Carol", "0901234567", "carol@example.com", "12 Nguyen Hue", null, null, null,
                PaymentMethod.COD, 202, 1442, "20308", null, carrier);
    }

    @Test
    void withoutACarrierTheCheaperOneIsUsed() {
        OrderResponse order = orders.checkout(request(null));
        assertThat(order.shippingSource()).isEqualTo(ShippingSource.GHTK);
        assertThat(order.shippingFee()).isEqualTo(31_000);
        assertThat(order.total()).isEqualTo(231_000);
    }

    @Test
    void theCustomersChoiceIsStoredEvenWhenItIsPricier() {
        OrderResponse order = orders.checkout(request("GHN"));
        assertThat(order.shippingSource()).isEqualTo(ShippingSource.GHN);
        assertThat(order.shippingFee()).isEqualTo(37_000);
        assertThat(order.total()).isEqualTo(237_000);
    }

    @Test
    void equalFeesDefaultToGhn() {
        ghtk.returnFee(37_000);
        assertThat(orders.checkout(request(null)).shippingSource()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void chosenCarrierThatStoppedQuotingIsRejectedAndNothingChanges() {
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThatThrownBy(() -> orders.checkout(request("GHTK"))).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SHIPPING_CARRIER_UNAVAILABLE);
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(orders.myOrders(0, 10).items()).isEmpty();
        assertThat(cart.getCart().items()).hasSize(1);
    }
}
