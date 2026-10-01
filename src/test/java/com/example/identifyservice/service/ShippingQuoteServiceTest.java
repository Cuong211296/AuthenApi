package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.MutableClock;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShippingQuoteServiceTest {
    static final GhtkProperties ON = new GhtkProperties("TEST-TOKEN", "TESTSRC", "https://ghtk.test",
            "Hà Nội", "Phường Test", null, null, "road");
    static final GhtkProperties OFF = new GhtkProperties("", "", "https://ghtk.test", "", "", null, null, "road");
    static final String TABLE_MESSAGE_DOWN = "Không kết nối được GHTK, dùng phí tạm tính";
    static final String TABLE_MESSAGE_UNSUPPORTED = "GHTK không hỗ trợ giao tới địa chỉ này, dùng phí tạm tính";

    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingService shipping;
    @Autowired CartMeasurer measurer;
    @Autowired TestDataFactory data;

    MutableClock clock;
    ShippingQuoteService service;
    ProductVariant tee;     // default weight 300, price 200.000
    ProductVariant hoodie;  // weight 700, base price 500.000, variant price 450.000

    @org.junit.jupiter.api.AfterEach
    void resetGhtk() {
        ghtk.reset();
    }

    @BeforeEach
    void setUp() {
        ghtk.reset();
        clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        service = serviceWith(ON);
        Product teeP = data.product("quote-tee", 200_000, true);
        tee = data.variant(teeP, "M", "white", 50, null);
        Product hoodieP = data.product("quote-hoodie", 500_000, true);
        hoodieP.setWeightGrams(700);
        hoodie = data.variant(hoodieP, "M", "black", 50, 450_000L);
    }

    private ShippingQuoteService serviceWith(GhtkProperties props) {
        return new ShippingQuoteService(ghtk, props, shipping, clock, measurer);
    }

    private Cart cart(Object... variantAndQty) {
        Cart cart = Cart.builder().user(data.user("quoter")).build();
        for (int i = 0; i < variantAndQty.length; i += 2)
            cart.getItems().add(CartItem.builder().cart(cart).variant((ProductVariant) variantAndQty[i])
                    .quantity((Integer) variantAndQty[i + 1]).build());
        return cart;
    }

    private ShippingQuote quote(Cart cart, String province) {
        return service.quote(cart, province, "Phường Bến Nghé", "12 Nguyen Hue");
    }

    @Test
    void ghtkFeeIsUsedAndWeightAndValueComeFromTheCart() {
        ghtk.returnFee(31_000);
        ShippingQuote q = quote(cart(tee, 2, hoodie, 1), "Hà Nội");

        assertThat(q).isEqualTo(new ShippingQuote(31_000, ShippingSource.GHTK, false, 1300, true, null));
        assertThat(ghtk.calls).hasSize(1);
        var call = ghtk.calls.get(0);
        assertThat(call.weightGrams()).isEqualTo(1300);          // 2 x 300 default + 1 x 700
        assertThat(call.value()).isEqualTo(850_000);             // 2 x 200.000 + 450.000 variant price
        assertThat(call.province()).isEqualTo("Hà Nội");
        assertThat(call.ward()).isEqualTo("Phường Bến Nghé");
        assertThat(call.address()).isEqualTo("12 Nguyen Hue");
    }

    @Test
    void fallsBackToTheTableOnEveryGhtkFailureMode() {
        Cart c = cart(tee, 1);

        ghtk.returnResult(new GhtkFeeResult(false, false, 0, "bad address"));
        assertThat(quote(c, "Hà Nội")).isEqualTo(
                new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, TABLE_MESSAGE_UNSUPPORTED));

        ghtk.returnResult(new GhtkFeeResult(true, false, 0, null));
        assertThat(quote(c, "Hà Nội")).isEqualTo(
                new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, TABLE_MESSAGE_UNSUPPORTED));

        ghtk.fail(new GhtkUnavailableException("down"));
        assertThat(quote(c, "Hà Nội")).isEqualTo(
                new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, TABLE_MESSAGE_DOWN));

        clock.advance(Duration.ofSeconds(61));                    // let the circuit breaker close again
        ghtk.fail(new IllegalStateException("boom"));
        assertThat(quote(c, "Hà Nội")).isEqualTo(
                new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, TABLE_MESSAGE_DOWN));
    }

    @Test
    void disabledConfigurationUsesTheTableWithoutCallingGhtk() {
        service = serviceWith(OFF);
        ghtk.returnFee(99_000);
        ShippingQuote q = quote(cart(tee, 1), "Hà Nội");
        assertThat(q).isEqualTo(new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, null));
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void cacheHitAvoidsASecondCallAndExpiresAfterTenMinutes() {
        ghtk.returnFee(31_000);
        Cart c = cart(tee, 1);
        quote(c, "Hà Nội");
        clock.advance(Duration.ofMinutes(9));
        assertThat(quote(c, "  hà nội ").fee()).isEqualTo(31_000);   // normalised key, still cached
        assertThat(ghtk.calls).hasSize(1);

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        ghtk.returnFee(32_000);
        assertThat(quote(c, "Hà Nội").fee()).isEqualTo(32_000);
        assertThat(ghtk.calls).hasSize(2);
    }

    @Test
    void failuresAreNotCached() {
        Cart c = cart(tee, 1);
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThat(quote(c, "Hà Nội").source()).isEqualTo(ShippingSource.TABLE);
        clock.advance(Duration.ofSeconds(61));                    // breaker window over
        ghtk.returnFee(31_000);
        assertThat(quote(c, "Hà Nội").source()).isEqualTo(ShippingSource.GHTK);
        assertThat(ghtk.calls).hasSize(2);
    }

    @Test
    void outageOpensAGlobalBreakerThatSkipsGhtkForSixtySecondsThenProbesAgain() {
        Cart c = cart(tee, 1);
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThat(quote(c, "Hà Nội").message()).isEqualTo(TABLE_MESSAGE_DOWN);
        assertThat(ghtk.calls).hasSize(1);

        // other keys (different address, weight) are skipped too: the breaker is global, not per key
        for (int i = 0; i < 5; i++) {
            ShippingQuote q = service.quote(cart(tee, i + 1), "Hà Nội", "Phường X", "addr " + i);
            assertThat(q.source()).isEqualTo(ShippingSource.TABLE);
            assertThat(q.message()).isEqualTo(TABLE_MESSAGE_DOWN);
        }
        assertThat(ghtk.calls).hasSize(1);

        clock.advance(Duration.ofSeconds(59));
        quote(c, "Hà Nội");
        assertThat(ghtk.calls).hasSize(1);

        clock.advance(Duration.ofSeconds(2));                     // window over: one probe, still failing
        assertThat(quote(c, "Hà Nội").message()).isEqualTo(TABLE_MESSAGE_DOWN);
        assertThat(ghtk.calls).hasSize(2);
        quote(c, "Hà Nội");                                       // re-opened for another 60 s
        assertThat(ghtk.calls).hasSize(2);

        clock.advance(Duration.ofSeconds(61));
        ghtk.returnFee(31_000);                                   // probe succeeds: breaker closes
        assertThat(quote(c, "Hà Nội").source()).isEqualTo(ShippingSource.GHTK);
        ghtk.fail(new GhtkUnavailableException("down again"));
        service.quote(cart(tee, 3), "Hà Nội", "Phường Y", "other");   // closed -> goes straight to GHTK again
        assertThat(ghtk.calls).hasSize(4);
    }

    @Test
    void cleanRefusalsDoNotOpenTheBreaker() {
        Cart c = cart(tee, 1);
        ghtk.returnResult(new GhtkFeeResult(true, false, 0, null));
        quote(c, "Hà Nội");
        quote(c, "Hà Nội");
        ghtk.returnResult(new GhtkFeeResult(false, false, 0, "bad"));
        quote(c, "Hà Nội");
        assertThat(ghtk.calls).hasSize(3);
    }

    @Test
    void onlyOneProbeRunsAfterTheWindowEvenWhenCallersRace() throws Exception {
        Cart c = cart(tee, 1);
        ghtk.fail(new GhtkUnavailableException("down"));
        quote(c, "Hà Nội");
        clock.advance(Duration.ofSeconds(61));

        var release = new java.util.concurrent.CountDownLatch(1);
        var entered = new java.util.concurrent.CountDownLatch(1);
        ghtk.handler = r -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new GhtkUnavailableException("interrupted");
            }
            throw new GhtkUnavailableException("still down");
        };
        var pool = java.util.concurrent.Executors.newFixedThreadPool(1);
        try {
            var probe = pool.submit(() -> service.quote(cart(tee, 1), "Hà Nội", "Phường Z", "slow"));
            entered.await();
            // probe in flight: every other caller is answered from the table without calling GHTK
            for (int i = 0; i < 5; i++) quote(c, "Hà Nội");
            assertThat(ghtk.calls).hasSize(2);
            release.countDown();
            assertThat(probe.get().message()).isEqualTo(TABLE_MESSAGE_DOWN);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentWeightOrValueBucketMakesANewCallButSameBucketDoesNot() {
        ghtk.returnFee(31_000);
        quote(cart(tee, 4), "Hà Nội");                  // value 800.000, weight 1200
        quote(cart(tee, 4), "Hà Nội");
        assertThat(ghtk.calls).hasSize(1);

        quote(cart(tee, 5), "Hà Nội");                  // value 1.000.000, weight 1500
        assertThat(ghtk.calls).hasSize(2);

        quote(cart(hoodie, 1, tee, 1), "Hà Nội");       // weight 1000, value 650.000
        assertThat(ghtk.calls).hasSize(3);
        quote(cart(hoodie, 1, tee, 1), "Hà Nội");
        assertThat(ghtk.calls).hasSize(3);

        // same weight (300 g), value in the same 100.000 bucket -> cache hit; next bucket -> new call
        Product pricey = data.product("quote-pricey", 250_000, true);
        ProductVariant p = data.variant(pricey, "M", "red", 5, null);
        quote(cart(tee, 1), "Hà Nội");
        int before = ghtk.calls.size();
        quote(cart(p, 1), "Hà Nội");                    // 250k -> bucket 200k, same as tee (200k)
        assertThat(ghtk.calls).hasSize(before);
        Product dear = data.product("quote-dear", 320_000, true);
        quote(cart(data.variant(dear, "M", "red", 5, null), 1), "Hà Nội");   // bucket 300k
        assertThat(ghtk.calls).hasSize(before + 1);
    }

    @Test
    void cacheIsBounded() {
        ghtk.returnFee(31_000);
        Cart c = cart(tee, 1);
        for (int i = 0; i < 520; i++) service.quote(c, "Hà Nội", "Phường A", "addr " + i);
        assertThat(ghtk.calls).hasSize(520);
        service.quote(c, "Hà Nội", "Phường A", "addr 0");      // evicted long ago
        assertThat(ghtk.calls).hasSize(521);
        service.quote(c, "Hà Nội", "Phường A", "addr 519");     // most recent, still cached
        assertThat(ghtk.calls).hasSize(521);
    }

    @Test
    void unknownProvinceWithGhtkErrorIsInvalidProvince() {
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThatThrownBy(() -> quote(cart(tee, 1), "Atlantis")).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_PROVINCE);
    }

    @Test
    void unknownProvinceWhenGhtkSaysNotDeliverableIsReportedNotDeliverable() {
        ghtk.returnResult(new GhtkFeeResult(true, false, 0, null));
        ShippingQuote q = quote(cart(tee, 1), "Atlantis");
        assertThat(q.deliverable()).isFalse();
        assertThat(q.fee()).isZero();
        assertThat(q.message()).isEqualTo("GHTK không hỗ trợ giao tới địa chỉ này");
    }

    @Test
    void unknownProvinceWithGhtkSuccessIsDeliverableAtTheGhtkFee() {
        ghtk.returnFee(40_000);
        ShippingQuote q = quote(cart(tee, 1), "Atlantis");
        assertThat(q.deliverable()).isTrue();
        assertThat(q.fee()).isEqualTo(40_000);
    }
}
