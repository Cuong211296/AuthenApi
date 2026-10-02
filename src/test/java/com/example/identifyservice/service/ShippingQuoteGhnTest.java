package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghn.GhnRejectedException;
import com.example.identifyservice.ghn.GhnUnavailableException;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.MutableClock;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShippingQuoteGhnTest {
    static final GhnProperties GHN_ON = new GhnProperties("GT", "1", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhnProperties GHN_OFF = new GhnProperties("", "", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhtkProperties GHTK_ON = new GhtkProperties("TEST-TOKEN", "TESTSRC", "https://ghtk.test",
            "Hà Nội", "Phường Test", null, null, "road");
    static final GhtkProperties GHTK_OFF = new GhtkProperties("", "", "https://ghtk.test", "", "", null, null, "road");
    static final String MSG_GHN_DOWN = "Không kết nối được GHN, dùng phí tạm tính";
    static final String MSG_GHTK_DOWN = "Không kết nối được GHTK, dùng phí tạm tính";

    // ids of the fake sample tree: TP HCM (202) > Quận 1 (1442) > Phường Bến Nghé (20308)
    static final QuoteAddress HCM_IDS = new QuoteAddress("client lies", "client lies", "client lies", "12 Nguyen Hue",
            202, 1442, "20308");

    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingService shipping;
    @Autowired CartMeasurer measurer;
    @Autowired TestDataFactory data;

    MutableClock clock;
    ShippingQuoteService service;
    ProductVariant tee;     // 300 g, 200.000
    ProductVariant hoodie;  // 700 g, 450.000

    @BeforeEach
    void setUp() {
        ghn.reset();
        ghtk.reset();
        ghn.useSampleData();
        clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        service = serviceWith(GHN_ON, GHTK_ON);
        Product teeP = data.product("ghnq-tee", 200_000, true);
        tee = data.variant(teeP, "M", "white", 50, null);
        Product hoodieP = data.product("ghnq-hoodie", 500_000, true);
        hoodieP.setWeightGrams(700);
        hoodie = data.variant(hoodieP, "M", "black", 50, 450_000L);
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
        ghtk.reset();
    }

    private ShippingQuoteService serviceWith(GhnProperties ghnProps, GhtkProperties ghtkProps) {
        return new ShippingQuoteService(ghtk, ghtkProps, ghn, ghnProps,
                new GhnMasterDataService(ghn, ghnProps, clock), shipping, clock, measurer);
    }

    private Cart cart(Object... variantAndQty) {
        Cart cart = Cart.builder().user(data.user("ghnq-user")).build();
        for (int i = 0; i < variantAndQty.length; i += 2)
            cart.getItems().add(CartItem.builder().cart(cart).variant((ProductVariant) variantAndQty[i])
                    .quantity((Integer) variantAndQty[i + 1]).build());
        return cart;
    }

    private ShippingQuote quoteIds(Cart c) {
        return service.quote(c, HCM_IDS);
    }

    private static QuoteAddress hanoiText(String address) {
        return QuoteAddress.text("Hà Nội", "Phường Phúc Xá", address);
    }

    @Test
    void ghnFeeIsUsedWithWeightAndInsuranceFromTheCart() {
        ghn.returnFee(37_000);
        ShippingQuote q = quoteIds(cart(tee, 2, hoodie, 1));

        assertThat(q).isEqualTo(new ShippingQuote(37_000, ShippingSource.GHN, false, 1300, true, null));
        assertThat(ghn.feeCalls).hasSize(1);
        var call = ghn.feeCalls.get(0);
        assertThat(call.toDistrictId()).isEqualTo(1442);
        assertThat(call.toWardCode()).isEqualTo("20308");
        assertThat(call.weightGrams()).isEqualTo(1300);
        assertThat(call.insuranceValue()).isEqualTo(850_000);
        assertThat(ghtk.calls).hasSize(1);   // GHTK is asked as well (the fake is down by default, so GHN's fee is still selected)
    }

    @Test
    void insuranceValueIsCappedAtFiveMillion() {
        ghn.returnFee(37_000);
        quoteIds(cart(tee, 30));                                  // 6.000.000 goods
        assertThat(ghn.feeCalls.get(0).insuranceValue()).isEqualTo(5_000_000);
    }

    @Test
    void namesAreResolvedFromMasterDataNeverTakenFromTheClient() {
        QuoteAddress resolved = service.resolveAddress(HCM_IDS);
        assertThat(resolved.provinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(resolved.districtName()).isEqualTo("Quận 1");
        assertThat(resolved.wardName()).isEqualTo("Phường Bến Nghé");
        assertThat(resolved.hasGhnIds()).isTrue();
        assertThat(resolved.address()).isEqualTo("12 Nguyen Hue");
    }

    @Test
    void ghnDownFallsBackToGhtkWithTheResolvedNames() {
        ghtk.returnFee(31_000);
        ShippingQuote q = quoteIds(cart(tee, 1));

        assertThat(q).isEqualTo(new ShippingQuote(31_000, ShippingSource.GHTK, false, 300, true, null));
        assertThat(ghn.feeCalls).hasSize(1);
        assertThat(ghtk.calls).hasSize(1);
        assertThat(ghtk.calls.get(0).province()).isEqualTo("Hồ Chí Minh");
        assertThat(ghtk.calls.get(0).ward()).isEqualTo("Phường Bến Nghé");
    }

    @Test
    void bothCarriersDownFallBackToTheTableByProvinceName() {
        ShippingQuote q = quoteIds(cart(tee, 1));       // GHN name "Hồ Chí Minh" matches the table's "TP Hồ Chí Minh"
        assertThat(q).isEqualTo(new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, MSG_GHTK_DOWN));
    }

    @Test
    void ghnDownAndGhtkDisabledFallsBackToTheTableWithAGhnMessage() {
        service = serviceWith(GHN_ON, GHTK_OFF);
        ShippingQuote q = quoteIds(cart(tee, 1));
        assertThat(q).isEqualTo(new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, MSG_GHN_DOWN));
    }

    @Test
    void ghnDownGhtkDownAndProvinceNotInTableIsNotAvailable() {
        ghn.provinceHandler = () -> java.util.List.of(new com.example.identifyservice.ghn.GhnProvince(900, "Atlantis"));
        ghn.districtHandler = id -> java.util.List.of(new com.example.identifyservice.ghn.GhnDistrict(9001, 900, "D"));
        ghn.wardHandler = id -> java.util.List.of(new com.example.identifyservice.ghn.GhnWard("W1", 9001, "W"));
        QuoteAddress atlantis = new QuoteAddress(null, null, null, "x", 900, 9001, "W1");
        assertThatThrownBy(() -> service.quote(cart(tee, 1), atlantis)).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SHIPPING_NOT_AVAILABLE);
    }

    @Test
    void disabledGhnUsesTheTextPathAndNeverCallsGhn() {
        service = serviceWith(GHN_OFF, GHTK_ON);
        ghn.returnFee(99_000);
        ghtk.returnFee(31_000);
        QuoteAddress withNamesAndIds = new QuoteAddress("Hà Nội", null, "Phường Phúc Xá", "1 St", 201, 1490, "1A0101");

        ShippingQuote q = service.quote(cart(tee, 1), withNamesAndIds);

        assertThat(q.source()).isEqualTo(ShippingSource.GHTK);
        assertThat(ghn.feeCalls).isEmpty();
        assertThat(ghn.provinceCalls).hasValue(0);
    }

    @Test
    void disabledGhnWithIdsOnlyIsInvalidInput() {
        service = serviceWith(GHN_OFF, GHTK_ON);
        assertInvalidInput(new QuoteAddress(null, null, null, "1 St", 202, 1442, "20308"));
    }

    @Test
    void idsThatDoNotMatchMasterDataAreInvalidInputAndNothingIsQuoted() {
        ghn.returnFee(37_000);
        assertInvalidInput(new QuoteAddress(null, null, null, "x", 999, 1442, "20308"));      // unknown province
        assertInvalidInput(new QuoteAddress(null, null, null, "x", 202, 1490, "1A0101"));      // district of Hà Nội
        assertInvalidInput(new QuoteAddress(null, null, null, "x", 202, 1442, "1A0101"));      // ward of another district
        assertInvalidInput(new QuoteAddress(null, null, null, "x", 202, 1442, "NOPE"));
        assertThat(ghn.feeCalls).isEmpty();
    }

    @Test
    void addressWithoutIdsOrTextNamesIsInvalidInput() {
        assertInvalidInput(new QuoteAddress(null, null, null, "x", null, null, null));
        assertInvalidInput(new QuoteAddress("Hà Nội", null, " ", "x", null, null, null));
        assertInvalidInput(new QuoteAddress(null, null, null, "x", null, 1442, "20308"));      // no province id
    }

    private void assertInvalidInput(QuoteAddress a) {
        assertThatThrownBy(() -> service.quote(cart(tee, 1), a)).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void masterDataDownWithTextNamesFallsBackToTheTextPathAndWithoutThemIsUnavailable() {
        ghn.masterDataDown();
        ghtk.returnFee(31_000);
        QuoteAddress withNames = new QuoteAddress("Hà Nội", "Ba Đình", "Phường Phúc Xá", "1 St", 201, 1490, "1A0101");
        QuoteAddress resolved = service.resolveAddress(withNames);
        assertThat(resolved.hasGhnIds()).isFalse();
        assertThat(resolved.provinceName()).isEqualTo("Hà Nội");
        assertThat(service.quote(cart(tee, 1), withNames).source()).isEqualTo(ShippingSource.GHTK);
        assertThat(ghn.feeCalls).isEmpty();

        assertThatThrownBy(() -> service.quote(cart(tee, 1), new QuoteAddress(null, null, null, "x", 202, 1442, "20308")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SHIPPING_PROVIDER_UNAVAILABLE);
    }

    @Test
    void ghnQuotesAreCachedForTenMinutesPerDistrictWardWeightAndValueBucket() {
        ghn.returnFee(37_000);
        Cart c = cart(tee, 1);
        quoteIds(c);
        clock.advance(Duration.ofMinutes(9));
        quoteIds(c);
        assertThat(ghn.feeCalls).hasSize(1);

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        ghn.returnFee(38_000);
        assertThat(quoteIds(c).fee()).isEqualTo(38_000);
        assertThat(ghn.feeCalls).hasSize(2);

        quoteIds(cart(tee, 2));                                   // other weight -> new call
        assertThat(ghn.feeCalls).hasSize(3);
        // another ward (same district/weight) -> new call; the 24 h master data cache must expire to see the new ward
        clock.advance(Duration.ofHours(25));
        ghn.returnFee(38_000);
        ghn.wardHandler = id -> java.util.List.of(FakeGhnGateway.BEN_NGHE,
                new com.example.identifyservice.ghn.GhnWard("20309", 1442, "Phường Bến Thành"));
        service.quote(c, new QuoteAddress(null, null, null, "x", 202, 1442, "20309"));
        assertThat(ghn.feeCalls).hasSize(4);
    }

    @Test
    void cachesAreKeyedPerCarrierSoAGhtkFeeIsNeverServedAsGhn() {
        ghtk.returnFee(31_000);
        Cart c = cart(tee, 1);
        assertThat(service.quote(c, hanoiText("1 St")).source()).isEqualTo(ShippingSource.GHTK);
        ghn.returnFee(37_000);
        // both carriers quote the ids address now: each keeps its own fee (the GHTK fee is never served as GHN's)
        var options = service.quoteOptionsResolved(CartMeasure.of(c), service.resolveAddress(HCM_IDS)).options();
        assertThat(options).extracting(ShippingQuote::source).containsExactly(ShippingSource.GHTK, ShippingSource.GHN);
        assertThat(options).extracting(ShippingQuote::fee).containsExactly(31_000L, 37_000L);
    }

    @Test
    void failuresAreNotCached() {
        Cart c = cart(tee, 1);
        assertThat(quoteIds(c).source()).isEqualTo(ShippingSource.TABLE);
        clock.advance(Duration.ofSeconds(61));
        ghn.returnFee(37_000);
        assertThat(quoteIds(c).source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void negativeFeeFromAGatewayIsTreatedAsAFailure() {
        ghn.feeHandler = r -> new com.example.identifyservice.ghn.GhnFeeResult(-1);
        assertThat(quoteIds(cart(tee, 1)).source()).isEqualTo(ShippingSource.TABLE);
    }

    @Test
    void ghnBreakerOpensForSixtySecondsThenProbesOnce() {
        Cart c = cart(tee, 1);
        quoteIds(c);
        assertThat(ghn.feeCalls).hasSize(1);
        for (int i = 0; i < 5; i++) service.quote(cart(tee, i + 2), HCM_IDS);   // different keys, still skipped
        assertThat(ghn.feeCalls).hasSize(1);

        clock.advance(Duration.ofSeconds(59));
        quoteIds(c);
        assertThat(ghn.feeCalls).hasSize(1);

        clock.advance(Duration.ofSeconds(2));                     // one probe, still failing -> re-opened
        quoteIds(c);
        assertThat(ghn.feeCalls).hasSize(2);
        quoteIds(c);
        assertThat(ghn.feeCalls).hasSize(2);

        clock.advance(Duration.ofSeconds(61));
        ghn.returnFee(37_000);                                    // probe succeeds, breaker closes
        assertThat(quoteIds(c).source()).isEqualTo(ShippingSource.GHN);
        ghn.failFee(new GhnUnavailableException("down again"));
        service.quote(cart(tee, 3), HCM_IDS);
        assertThat(ghn.feeCalls).hasSize(4);
    }

    @Test
    void breakersArePerCarrier() {
        // GHN down opens only the GHN breaker: GHTK keeps being called for the same quote
        ghtk.returnFee(31_000);
        quoteIds(cart(tee, 1));
        quoteIds(cart(tee, 2));
        assertThat(ghn.feeCalls).hasSize(1);
        assertThat(ghtk.calls).hasSize(2);

        // GHTK down opens only the GHTK breaker: GHN keeps working
        ghn.returnFee(37_000);
        clock.advance(Duration.ofSeconds(61));
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThat(service.quote(cart(tee, 1), hanoiText("a")).source()).isEqualTo(ShippingSource.TABLE);
        assertThat(service.quote(cart(tee, 1), hanoiText("b")).message()).isEqualTo(MSG_GHTK_DOWN);
        assertThat(ghtk.calls).hasSize(3);                        // second text quote skipped by the open GHTK breaker
        assertThat(quoteIds(cart(tee, 4)).source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void aCleanGhnRejectionFallsBackWithoutOpeningTheBreaker() {
        ghn.failFee(new GhnRejectedException("weight"));
        quoteIds(cart(tee, 1));
        quoteIds(cart(tee, 2));
        quoteIds(cart(tee, 3));
        assertThat(ghn.feeCalls).hasSize(3);
    }

    @Test
    void onlyOneGhnProbeRunsAfterTheWindowEvenWhenCallersRace() throws Exception {
        Cart c = cart(tee, 1);
        quoteIds(c);
        clock.advance(Duration.ofSeconds(61));

        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        ghn.feeHandler = r -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new GhnUnavailableException("interrupted");
            }
            throw new GhnUnavailableException("still down");
        };
        var pool = Executors.newFixedThreadPool(1);
        try {
            var probe = pool.submit(() -> service.quote(cart(tee, 1), HCM_IDS));
            entered.await();
            for (int i = 0; i < 5; i++) quoteIds(c);
            assertThat(ghn.feeCalls).hasSize(2);                  // 1 initial + the single in-flight probe
            release.countDown();
            assertThat(probe.get().source()).isEqualTo(ShippingSource.TABLE);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aProbeThatThrowsAnErrorDoesNotLeaveTheBreakerStuckOpen() {
        Cart c = cart(tee, 1);
        quoteIds(c);
        clock.advance(Duration.ofSeconds(61));
        ghn.feeHandler = r -> {
            throw new StackOverflowError("boom");
        };
        assertThatThrownBy(() -> quoteIds(c)).isInstanceOf(StackOverflowError.class);

        ghn.returnFee(37_000);                                    // the next caller may probe again immediately
        assertThat(quoteIds(c).source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void ghnFeeFailureForAMergedOldProvinceFallsBackToTheMergedUnitsTableRate() {
        ghn.provinceHandler = () -> java.util.List.of(new com.example.identifyservice.ghn.GhnProvince(203, "Bình Dương"));
        ghn.districtHandler = id -> java.util.List.of(new com.example.identifyservice.ghn.GhnDistrict(2031, 203, "Thủ Dầu Một"));
        ghn.wardHandler = id -> java.util.List.of(new com.example.identifyservice.ghn.GhnWard("W203", 2031, "Phường Phú Cường"));
        service = serviceWith(GHN_ON, GHTK_OFF);
        QuoteAddress bd = new QuoteAddress(null, null, null, "1 St", 203, 2031, "W203");

        ShippingQuote q = service.quote(cart(tee, 1), bd);

        assertThat(q).isEqualTo(new ShippingQuote(25_000, ShippingSource.TABLE, true, 300, true, MSG_GHN_DOWN));
        assertThat(service.resolveAddress(bd).provinceName()).isEqualTo("Bình Dương");   // order keeps the GHN name
    }

    @Test
    void providerConfigPicksGhnThenGhtkThenTable() {
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("GHN", "GHN_IDS"));

        ghn.masterDataDown();
        ShippingQuoteService down = serviceWith(GHN_ON, GHTK_ON);
        assertThat(down.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("GHTK", "TEXT"));
        assertThat(serviceWith(GHN_ON, GHTK_OFF).providerConfig())
                .isEqualTo(new ShippingQuoteService.ProviderConfig("TABLE", "TEXT"));

        ghn.useSampleData();
        assertThat(serviceWith(GHN_OFF, GHTK_ON).providerConfig())
                .isEqualTo(new ShippingQuoteService.ProviderConfig("GHTK", "TEXT"));
        assertThat(serviceWith(GHN_OFF, GHTK_OFF).providerConfig())
                .isEqualTo(new ShippingQuoteService.ProviderConfig("TABLE", "TEXT"));
    }

    @Test
    void unexpectedRuntimeExceptionsFromGhnCountAsFailures() {
        ghn.failFee(new IllegalStateException("boom"));
        assertThat(quoteIds(cart(tee, 1)).source()).isEqualTo(ShippingSource.TABLE);
        quoteIds(cart(tee, 2));
        assertThat(ghn.feeCalls).hasSize(1);
    }
}
