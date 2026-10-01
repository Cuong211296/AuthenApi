package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.ShopSettingsRequest;
import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.ShopSettings;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.ghn.GhnFeeResult;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghn.GhnUnavailableException;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.repository.ShopSettingsRepository;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.MutableClock;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Quote integration of the editable pickup address. Not @Transactional: settings are really committed. */
@SpringBootTest
@ActiveProfiles("test")
class ShopSettingsQuoteTest {
    static final GhnProperties GHN_ON = new GhnProperties("GT", "1", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhnProperties GHN_OFF = new GhnProperties("", "", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhtkProperties GHTK_ENV = new GhtkProperties("T", "S", "https://ghtk.test", "Hà Nội", "Phường Test",
            "Quận Env", "Env street", "road");
    static final GhtkProperties GHTK_CREDS_ONLY = new GhtkProperties("T", "S", "https://ghtk.test", "", "", null,
            null, "road");
    static final CartMeasure MEASURE = new CartMeasure(300, 200_000);
    // destination: Hà Nội > Quận Ba Đình > Phường Phúc Xá (fake sample tree)
    static final QuoteAddress HANOI_IDS = new QuoteAddress("x", "x", "x", "1 Hàng Bài", 201, 1490, "1A0101");

    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingService shipping;
    @Autowired CartMeasurer measurer;
    @Autowired ShopSettingsRepository repository;
    @Autowired DataSource dataSource;

    MutableClock clock;
    ShopSettingsService settings;
    ShippingQuoteService service;

    @BeforeEach
    void setUp() {
        ghn.reset();
        ghtk.reset();
        ghn.useSampleData();
        repository.deleteAll();
        clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        build(GHN_ON, GHTK_ENV);
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
        ghtk.reset();
        repository.deleteAll();
    }

    private void build(GhnProperties ghnProps, GhtkProperties ghtkProps) {
        GhnMasterDataService masterData = new GhnMasterDataService(ghn, ghnProps, clock);
        settings = new ShopSettingsService(repository, masterData, ghnProps, ghtkProps, clock);
        service = new ShippingQuoteService(ghtk, ghtkProps, ghn, ghnProps, masterData, shipping, clock, measurer,
                settings);
    }

    private static ShopSettingsRequest hcmPickup() {
        return new ShopSettingsRequest("Shop", null, new PickupAddress(202, 1442, "20308", null, null, null, "12 NH"));
    }

    private static ShopSettingsRequest hanoiPickup() {
        return new ShopSettingsRequest("Shop", null, new PickupAddress(201, 1490, "1A0101", null, null, null, null));
    }

    @Test
    void withoutSettingsNoFromFieldsAreSent() {
        ghn.returnFee(30_000);
        assertThat(service.quote(MEASURE, HANOI_IDS).source()).isEqualTo(ShippingSource.GHN);
        assertThat(ghn.feeCalls).hasSize(1);
        assertThat(ghn.feeCalls.get(0).fromDistrictId()).isNull();
        assertThat(ghn.feeCalls.get(0).fromWardCode()).isNull();
    }

    @Test
    void afterAnUpdateBothFromDistrictAndFromWardAreSent() {
        ghn.returnFee(30_000);
        settings.update(hcmPickup(), "admin");
        service.quote(MEASURE, HANOI_IDS);
        var call = ghn.feeCalls.get(0);
        assertThat(call.fromDistrictId()).isEqualTo(1442);
        assertThat(call.fromWardCode()).isEqualTo("20308");
        assertThat(call.toDistrictId()).isEqualTo(1490);
    }

    @Test
    void onlyADistrictWithoutWardSendsNoFromFields() {
        repository.save(ShopSettings.builder().id("main").shopName("S").provinceId(202).districtId(1442).build());
        settings.invalidateSnapshot();
        ghn.returnFee(30_000);
        service.quote(MEASURE, HANOI_IDS);
        assertThat(ghn.feeCalls.get(0).fromDistrictId()).isNull();
        assertThat(ghn.feeCalls.get(0).fromWardCode()).isNull();
    }

    @Test
    void anOldOriginQuoteIsNotReusedAfterTheOriginChanges() {
        settings.update(hcmPickup(), "admin");
        ghn.returnFee(20_900);
        assertThat(service.quote(MEASURE, HANOI_IDS).fee()).isEqualTo(20_900);
        assertThat(service.quote(MEASURE, HANOI_IDS).fee()).isEqualTo(20_900);
        assertThat(ghn.feeCalls).hasSize(1);                      // cached

        ghn.returnFee(53_900);
        settings.update(hanoiPickup(), "admin");
        assertThat(service.quote(MEASURE, HANOI_IDS).fee()).isEqualTo(53_900);
        assertThat(ghn.feeCalls).hasSize(2);
        assertThat(ghn.feeCalls.get(1).fromDistrictId()).isEqualTo(1490);
        assertThat(ghn.feeCalls.get(1).fromWardCode()).isEqualTo("1A0101");
    }

    @Test
    void theCacheKeyItselfCarriesThePickupSoAChangeWithoutClearingCannotReuseAQuote() {
        settings.update(hcmPickup(), "admin");
        ghn.returnFee(20_900);
        service.quote(MEASURE, HANOI_IDS);
        // change the stored origin behind the service's back (no listener, no cache clear)
        var row = repository.findById("main").orElseThrow();
        row.setDistrictId(1490);
        row.setWardCode("1A0101");
        repository.save(row);
        settings.invalidateSnapshot();
        ghn.returnFee(53_900);
        assertThat(service.quote(MEASURE, HANOI_IDS).fee()).isEqualTo(53_900);
    }

    @Test
    void anUpdateDoesNotTouchTheCircuitBreaker() {
        ghn.failFee(new GhnUnavailableException("down"));
        service.quote(MEASURE, HANOI_IDS);                        // opens the GHN breaker
        assertThat(ghn.feeCalls).hasSize(1);

        settings.update(hcmPickup(), "admin");
        ghn.returnFee(30_000);
        service.quote(MEASURE, HANOI_IDS);
        service.quote(new CartMeasure(500, 200_000), HANOI_IDS);
        assertThat(ghn.feeCalls).hasSize(1);                      // still skipped: the update did not reset it
    }

    @Test
    void ghtkPickComesFromEnvWithoutSettingsAndFromSettingsNamesAfterwards() {
        build(GHN_OFF, GHTK_ENV);
        ghtk.returnFee(31_000);
        QuoteAddress dest = QuoteAddress.text("Hà Nội", "Phường Phúc Xá", "1 Hàng Bài");
        service.quote(MEASURE, dest);
        var env = ghtk.calls.get(0);
        assertThat(env.pickProvince()).isEqualTo("Hà Nội");
        assertThat(env.pickWard()).isEqualTo("Phường Test");
        assertThat(env.pickDistrict()).isEqualTo("Quận Env");
        assertThat(env.pickAddress()).isEqualTo("Env street");

        settings.update(new ShopSettingsRequest("Shop", null, new PickupAddress(null, null, null, "Đà Nẵng",
                "Quận Hải Châu", "Phường Thạch Thang", "1 Bạch Đằng")), "admin");
        service.quote(MEASURE, dest);
        assertThat(ghtk.calls).hasSize(2);                        // not served from the old-origin cache
        var set = ghtk.calls.get(1);
        assertThat(set.pickProvince()).isEqualTo("Đà Nẵng");
        assertThat(set.pickWard()).isEqualTo("Phường Thạch Thang");
        assertThat(set.pickDistrict()).isEqualTo("Quận Hải Châu");
        assertThat(set.pickAddress()).isEqualTo("1 Bạch Đằng");
    }

    @Test
    void ghtkBecomesEnabledWhenOnlySettingsProvideThePickup() {
        build(GHN_OFF, GHTK_CREDS_ONLY);
        ghtk.returnFee(31_000);
        QuoteAddress dest = QuoteAddress.text("Hà Nội", "Phường Phúc Xá", "1 Hàng Bài");
        assertThat(service.quote(MEASURE, dest).source()).isEqualTo(ShippingSource.TABLE);
        assertThat(ghtk.calls).isEmpty();
        assertThat(service.providerConfig().provider()).isEqualTo("TABLE");

        settings.update(new ShopSettingsRequest("Shop", null,
                new PickupAddress(null, null, null, "Hà Nội", null, "Phường Hàng Bài", null)), "admin");
        ShippingQuote q = service.quote(MEASURE, dest);
        assertThat(q.source()).isEqualTo(ShippingSource.GHTK);
        assertThat(ghtk.calls.get(0).pickWard()).isEqualTo("Phường Hàng Bài");
        assertThat(service.providerConfig().provider()).isEqualTo("GHTK");
    }

    @Test
    void settingsPickupWinsOverEnvAndEnvOverTheCarrierDefault() {
        GhnProperties withEnv = new GhnProperties("GT", "1", "https://ghn.test", 555, 2, 25, 20, 10);
        build(withEnv, GHTK_ENV);
        ghn.returnFee(30_000);
        service.quote(MEASURE, HANOI_IDS);
        assertThat(ghn.feeCalls.get(0).fromDistrictId()).isNull();   // request carries no pair: the gateway uses env 555

        settings.update(hcmPickup(), "admin");
        service.quote(MEASURE, HANOI_IDS);
        assertThat(ghn.feeCalls.get(1).fromDistrictId()).isEqualTo(1442);   // settings beat env
    }

    @Test
    void anUpdateWhileACarrierCallIsInFlightNeitherBlocksNorIsBlockedAndTheQuotePathHoldsNoConnection() throws Exception {
        settings.update(hcmPickup(), "admin");                    // warms the snapshot
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        CountDownLatch inCarrier = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger activeDuringCall = new AtomicInteger(-1);
        ghn.feeHandler = r -> {
            activeDuringCall.set(hikari.getHikariPoolMXBean().getActiveConnections());
            inCarrier.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new GhnFeeResult(20_900);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ShippingQuote> quote = CompletableFuture.supplyAsync(
                    () -> service.quote(MEASURE, HANOI_IDS), pool);
            assertThat(inCarrier.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(activeDuringCall.get()).isZero();          // no DB connection held during the carrier call

            CompletableFuture<Object> update = CompletableFuture.supplyAsync(
                    () -> settings.update(hanoiPickup(), "admin"), pool);
            update.get(10, TimeUnit.SECONDS);                     // completes while the carrier call is blocked
            assertThat(quote.isDone()).isFalse();
            assertThat(settings.pickup().districtId()).isEqualTo(1490);

            release.countDown();
            assertThat(quote.get(10, TimeUnit.SECONDS).fee()).isEqualTo(20_900);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void theQuotePathDoesNotQueryTheDatabaseOnceTheSnapshotIsLoaded() {
        settings.update(hcmPickup(), "admin");
        ghn.returnFee(30_000);
        repository.deleteAll();                                   // the row is gone: only the snapshot can answer
        service.quote(MEASURE, HANOI_IDS);
        assertThat(ghn.feeCalls.get(0).fromDistrictId()).isEqualTo(1442);
    }
}
