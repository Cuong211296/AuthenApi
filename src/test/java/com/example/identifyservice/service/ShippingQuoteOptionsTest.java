package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CarrierSwitchesRequest;
import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnFeeResult;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghn.GhnUnavailableException;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import com.example.identifyservice.repository.ShopSettingsRepository;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShippingQuoteOptionsTest {
    static final GhnProperties GHN_ON = new GhnProperties("GT", "1", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhtkProperties GHTK_ON = new GhtkProperties("TEST-TOKEN", "TESTSRC", "https://ghtk.test",
            "Hà Nội", "Phường Test", null, null, "road");
    static final GhtkProperties GHTK_OFF = new GhtkProperties("", "", "https://ghtk.test", "", "", null, null, "road");
    // ids of the fake sample tree: TP HCM (202) > Quận 1 (1442) > Phường Bến Nghé (20308)
    static final QuoteAddress HCM_IDS = new QuoteAddress("x", "x", "x", "12 Nguyen Hue", 202, 1442, "20308");

    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingService shipping;
    @Autowired CartMeasurer measurer;
    @Autowired TestDataFactory data;
    @Autowired ShopSettingsRepository repository;

    MutableClock clock;
    ShopSettingsService settings;
    ShippingQuoteService service;
    ProductVariant tee;

    @BeforeEach
    void setUp() {
        ghn.reset();
        ghtk.reset();
        ghn.useSampleData();
        repository.deleteAll();
        clock = new MutableClock(Instant.parse("2026-10-02T00:00:00Z"));
        build(GHN_ON, GHTK_ON);
        tee = data.variant(data.product("opt-tee", 200_000, true), "M", "white", 50, null);
        ghn.returnFee(38_500);
        ghtk.returnFee(32_000);
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
        ghtk.reset();
        repository.deleteAll();
    }

    private void build(GhnProperties g, GhtkProperties k) {
        GhnMasterDataService md = new GhnMasterDataService(ghn, g, clock);
        settings = new ShopSettingsService(repository, md, g, k, clock);
        service = new ShippingQuoteService(ghtk, k, ghn, g, md, shipping, clock, measurer, settings);
    }

    private Cart cart() {
        Cart cart = Cart.builder().user(data.user("opt-user")).build();
        cart.getItems().add(CartItem.builder().cart(cart).variant(tee).quantity(1).build());
        return cart;
    }

    private ShippingQuoteService.QuoteOptions options() {
        return service.quoteOptionsResolved(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS));
    }

    private void switches(boolean ghnOn, boolean ghtkOn) {
        settings.updateCarriers(new CarrierSwitchesRequest(ghnOn, ghtkOn), "boss");
    }

    private static List<ShippingSource> sources(ShippingQuoteService.QuoteOptions o) {
        return o.options().stream().map(ShippingQuote::source).toList();
    }

    @Test
    void bothQuoteCheaperIsSelectedAndOptionsAreCheapestFirst() {
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHTK, ShippingSource.GHN);
        assertThat(o.selected()).isEqualTo(o.options().get(0));
        assertThat(o.selected().fee()).isEqualTo(32_000);
    }

    @Test
    void ghnIsSelectedWhenItIsCheaper() {
        ghn.returnFee(20_000);
        assertThat(options().selected().source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void equalFeesSelectGhn() {
        ghn.returnFee(30_000);
        ghtk.returnFee(30_000);
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHN, ShippingSource.GHTK);
        assertThat(o.selected().source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void aCarrierThatCannotQuoteIsLeftOutNotAnError() {
        ghtk.fail(new GhtkUnavailableException("down"));
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHN);
        assertThat(o.selected().source()).isEqualTo(ShippingSource.GHN);

        ghtk.reset();
        service.clearCache();
        ghn.useSampleData();
        ghn.returnFee(38_500);
        ghtk.returnResult(new GhtkFeeResult(false, false, 0, "refused"));
        assertThat(sources(options())).containsExactly(ShippingSource.GHN);
    }

    @Test
    void noCarrierQuotingFallsBackToTheTableWithNoOptions() {
        ghn.failFee(new GhnUnavailableException("down"));
        ghtk.fail(new GhtkUnavailableException("down"));
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(o.options()).isEmpty();
        assertThat(o.selected().source()).isEqualTo(ShippingSource.TABLE);
        assertThat(o.selected().estimated()).isTrue();
    }

    @Test
    void switchedOffGhnIsNeverAskedAndGhtkIsTheOnlyOption() {
        switches(false, true);
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHTK);
        assertThat(ghn.feeCalls).isEmpty();
    }

    @Test
    void switchedOffGhtkIsNeverAsked() {
        switches(true, false);
        assertThat(sources(options())).containsExactly(ShippingSource.GHN);
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void bothSwitchedOffUsesTheTable() {
        switches(false, false);
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(o.options()).isEmpty();
        assertThat(o.selected().source()).isEqualTo(ShippingSource.TABLE);
        assertThat(o.selected().message()).isNull();
        assertThat(ghn.feeCalls).isEmpty();
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void unconfiguredCarrierIsNeverQueriedEvenWhenSwitchedOn() {
        build(GHN_ON, GHTK_OFF);
        switches(true, true);
        assertThat(sources(options())).containsExactly(ShippingSource.GHN);
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void aSwitchChangeClearsCachedQuotes() {
        assertThat(options().options()).hasSize(2);
        ghn.returnFee(10_000);
        assertThat(options().selected().fee()).isEqualTo(32_000);          // GHN still cached at 38.500
        switches(true, true);                                              // any change fires the listeners
        assertThat(options().selected().source()).isEqualTo(ShippingSource.GHN);
        assertThat(options().selected().fee()).isEqualTo(10_000);
    }

    @Test
    void providerConfigFollowsTheSwitchesButTheAddressModeDoesNot() {
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("GHN", "GHN_IDS"));
        switches(false, true);
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("GHTK", "GHN_IDS"));
        switches(false, false);
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("TABLE", "GHN_IDS"));
    }

    @Test
    void quoteForCarrierReturnsTheChosenCarrierEvenWhenItIsPricier() {
        ShippingQuote q = service.quoteForCarrier(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS),
                ShippingSource.GHN);
        assertThat(q.source()).isEqualTo(ShippingSource.GHN);
        assertThat(q.fee()).isEqualTo(38_500);
    }

    @Test
    void quoteForCarrierWithoutAChoiceIsTheCheapest() {
        ShippingQuote q = service.quoteForCarrier(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS), null);
        assertThat(q.source()).isEqualTo(ShippingSource.GHTK);
    }

    @Test
    void quoteForCarrierThatNoLongerQuotesIsRejectedNotSwitched() {
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThatThrownBy(() -> service.quoteForCarrier(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS),
                ShippingSource.GHTK))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SHIPPING_CARRIER_UNAVAILABLE));
    }

    @Test
    void carriersAreQuotedInParallel() {
        ghn.feeHandler = r -> {
            sleep(300);
            return new GhnFeeResult(38_500);
        };
        ghtk.handler = r -> {
            sleep(300);
            return new GhtkFeeResult(true, true, 32_000, null);
        };
        long start = System.nanoTime();
        assertThat(options().options()).hasSize(2);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(550);        // sequential would be 600+
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
