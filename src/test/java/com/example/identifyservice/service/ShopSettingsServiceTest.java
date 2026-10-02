package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CarrierSwitchesRequest;
import com.example.identifyservice.dto.request.ShopSettingsRequest;
import com.example.identifyservice.dto.response.ShopSettingsResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.repository.ShopSettingsRepository;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ShopSettingsServiceTest {
    static final GhnProperties GHN_ON = new GhnProperties("GT", "777", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhnProperties GHN_OFF = new GhnProperties("", "", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhtkProperties GHTK_ON = new GhtkProperties("T", "S", "https://ghtk.test", "Hà Nội", "Phường Test",
            null, null, "road");
    static final GhtkProperties GHTK_OFF = new GhtkProperties("", "", "https://ghtk.test", "", "", null, null, "road");

    @Autowired FakeGhnGateway ghn;
    @Autowired ShopSettingsRepository repository;
    @Autowired ShopSettingsService autowiredService;

    MutableClock clock;

    @BeforeEach
    void setUp() {
        ghn.reset();
        ghn.useSampleData();
        repository.deleteAll();
        autowiredService.invalidateSnapshot();
        clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
        repository.deleteAll();
        autowiredService.invalidateSnapshot();
    }

    private ShopSettingsService service(GhnProperties ghnProps, GhtkProperties ghtkProps) {
        return new ShopSettingsService(repository, new GhnMasterDataService(ghn, ghnProps, clock), ghnProps, ghtkProps,
                clock);
    }

    static PickupAddress ids(int province, int district, String ward, String street) {
        return new PickupAddress(province, district, ward, "client lies", "client lies", "client lies", street);
    }

    static PickupAddress hcmIds() {
        return ids(202, 1442, "20308", "12 Nguyễn Huệ");
    }

    static PickupAddress names(String province, String district, String ward, String street) {
        return new PickupAddress(null, null, null, province, district, ward, street);
    }

    static ShopSettingsRequest req(String shop, String phone, PickupAddress pickup) {
        return new ShopSettingsRequest(shop, phone, pickup);
    }

    private static void assertInvalid(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void emptyWhenNoRowYet() {
        ShopSettingsResponse r = service(GHN_ON, GHTK_OFF).get();
        assertThat(r.shopName()).isNull();
        assertThat(r.pickup()).isEqualTo(PickupAddress.EMPTY);
        assertThat(r.updatedAt()).isNull();
        assertThat(r.addressMode()).isEqualTo("GHN_IDS");
        assertThat(service(GHN_ON, GHTK_OFF).pickup()).isEqualTo(PickupAddress.EMPTY);
    }

    @Test
    void ghnIdsAreValidatedAndNamedFromMasterDataNeverFromTheClient() {
        ShopSettingsService s = service(GHN_ON, GHTK_OFF);
        ShopSettingsResponse r = s.update(req("  Quini Bear ", "0901234567", hcmIds()), "admin");

        assertThat(r.shopName()).isEqualTo("Quini Bear");
        assertThat(r.pickup()).isEqualTo(new PickupAddress(202, 1442, "20308", "Hồ Chí Minh", "Quận 1",
                "Phường Bến Nghé", "12 Nguyễn Huệ"));
        assertThat(r.updatedBy()).isEqualTo("admin");
        assertThat(r.updatedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(r.carriers().ghn().configured()).isTrue();
        assertThat(r.carriers().ghn().shopId()).isEqualTo("777");
        assertThat(r.carriers().ghtk().configured()).isFalse();
        assertThat(s.pickup()).isEqualTo(r.pickup());
    }

    @Test
    void wardOfAnotherDistrictIsInvalid() {
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, ids(202, 1442, "1A0101", null)), "a"));
    }

    @Test
    void districtOfAnotherProvinceIsInvalid() {
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, ids(202, 1490, "1A0101", null)), "a"));
    }

    @Test
    void unknownIdsAreInvalid() {
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, ids(999, 1442, "20308", null)), "a"));
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, ids(202, 9999, "20308", null)), "a"));
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, ids(202, 1442, "NOPE", null)), "a"));
    }

    @Test
    void ghnEnabledRequiresIdsNotNames() {
        assertInvalid(() -> service(GHN_ON, GHTK_OFF)
                .update(req("Shop", null, names("Hà Nội", null, "Phường Phúc Xá", null)), "a"));
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null,
                new PickupAddress(202, 1442, " ", null, null, null, null)), "a"));
        assertInvalid(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, PickupAddress.EMPTY), "a"));
    }

    @Test
    void masterDataDownIsProviderUnavailableAndNothingIsSaved() {
        ghn.masterDataDown();
        assertThatThrownBy(() -> service(GHN_ON, GHTK_OFF).update(req("Shop", null, hcmIds()), "a"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SHIPPING_PROVIDER_UNAVAILABLE));
        assertThat(repository.count()).isZero();
    }

    @Test
    void ghnDisabledTakesNamesAndRejectsIds() {
        ShopSettingsService s = service(GHN_OFF, GHTK_OFF);
        ShopSettingsResponse r = s.update(req("Shop", null,
                names(" Hà Nội ", null, "Phường Phúc Xá", "5 Hàng Bài")), "admin");
        assertThat(r.addressMode()).isEqualTo("TEXT");
        assertThat(r.pickup()).isEqualTo(names("Hà Nội", null, "Phường Phúc Xá", "5 Hàng Bài"));
        assertThat(r.carriers().ghn().configured()).isFalse();
        assertThat(r.carriers().ghn().shopId()).isNull();

        assertInvalid(() -> s.update(req("Shop", null, hcmIds()), "a"));
        assertInvalid(() -> s.update(req("Shop", null, names(null, null, "Phường X", null)), "a"));
        assertInvalid(() -> s.update(req("Shop", null, names("Hà Nội", null, " ", null)), "a"));
        assertInvalid(() -> s.update(req("Shop", null, new PickupAddress(null, 5, null, "Hà Nội", null, "P", null)), "a"));
    }

    @Test
    void shopNameIsRequiredAndBounded() {
        ShopSettingsService s = service(GHN_ON, GHTK_OFF);
        assertInvalid(() -> s.update(req(null, null, hcmIds()), "a"));
        assertInvalid(() -> s.update(req("  ", null, hcmIds()), "a"));
        assertInvalid(() -> s.update(req("x".repeat(101), null, hcmIds()), "a"));
        assertInvalid(() -> s.update(req("Shop", null, null), "a"));
        assertInvalid(() -> s.update(req("Shop", null, ids(202, 1442, "20308", "y".repeat(301))), "a"));
    }

    @Test
    void phoneIsOptionalButMustMatchThePattern() {
        ShopSettingsService s = service(GHN_ON, GHTK_OFF);
        assertThat(s.update(req("Shop", null, hcmIds()), "a").phone()).isNull();
        assertThat(s.update(req("Shop", "  ", hcmIds()), "a").phone()).isNull();
        assertThat(s.update(req("Shop", "+84901234567", hcmIds()), "a").phone()).isEqualTo("+84901234567");
        for (String bad : new String[]{"12345", "090123456", "09012345678", "abc0901234", "0901 234 567"})
            assertInvalid(() -> s.update(req("Shop", bad, hcmIds()), "a"));
    }

    @Test
    void invalidUpdateKeepsThePreviousSettings() {
        ShopSettingsService s = service(GHN_ON, GHTK_OFF);
        s.update(req("First", null, hcmIds()), "a");
        assertInvalid(() -> s.update(req("Second", null, ids(202, 1442, "NOPE", null)), "a"));
        assertThat(s.get().shopName()).isEqualTo("First");
        assertThat(s.pickup().wardCode()).isEqualTo("20308");
    }

    @Test
    void settingsSurviveAFreshServiceInstance() {
        service(GHN_ON, GHTK_OFF).update(req("Shop", "0901234567", hcmIds()), "admin");

        ShopSettingsService fresh = service(GHN_ON, GHTK_OFF);
        assertThat(fresh.pickup()).isEqualTo(new PickupAddress(202, 1442, "20308", "Hồ Chí Minh", "Quận 1",
                "Phường Bến Nghé", "12 Nguyễn Huệ"));
        ShopSettingsResponse r = fresh.get();
        assertThat(r.shopName()).isEqualTo("Shop");
        assertThat(r.phone()).isEqualTo("0901234567");
        assertThat(r.updatedBy()).isEqualTo("admin");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("main")).isPresent();
    }

    @Test
    void updateReplacesTheSingleRowAndRefreshesTheSnapshot() {
        ShopSettingsService s = service(GHN_ON, GHTK_OFF);
        s.update(req("One", null, hcmIds()), "a");
        assertThat(s.pickup().districtId()).isEqualTo(1442);
        s.update(req("Two", null, ids(201, 1490, "1A0101", null)), "b");
        assertThat(s.pickup().districtId()).isEqualTo(1490);
        assertThat(s.get().updatedBy()).isEqualTo("b");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void ghtkEnabledReflectsSettingsOrEnvPickup() {
        // GHTK credentials but no env pick fields: enabled only once the settings give names
        GhtkProperties credsOnly = new GhtkProperties("T", "S", "https://ghtk.test", "", "", null, null, "road");
        ShopSettingsService s = service(GHN_OFF, credsOnly);
        assertThat(s.get().carriers().ghtk().configured()).isFalse();
        s.update(req("Shop", null, names("Hà Nội", null, "Phường Phúc Xá", null)), "a");
        assertThat(s.get().carriers().ghtk().configured()).isTrue();
        assertThat(service(GHN_OFF, GHTK_ON).get().carriers().ghtk().enabled()).isTrue();
    }

    @Test
    void carrierSwitchesDefaultToOnPersistAndSurviveAReload() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        assertThat(s.carrierSwitches()).isEqualTo(CarrierSwitches.ALL_ON);

        ShopSettingsResponse r = s.updateCarriers(new CarrierSwitchesRequest(false, true), "boss");

        assertThat(r.carriers().ghn().configured()).isTrue();
        assertThat(r.carriers().ghn().enabled()).isFalse();
        assertThat(r.carriers().ghtk().enabled()).isTrue();
        assertThat(r.updatedBy()).isEqualTo("boss");
        assertThat(s.carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
        // a fresh service has an empty snapshot and must read the stored row
        assertThat(service(GHN_ON, GHTK_ON).carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
    }

    @Test
    void changingTheSwitchesNotifiesListeners() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        AtomicInteger calls = new AtomicInteger();
        s.addChangeListener(calls::incrementAndGet);
        s.updateCarriers(new CarrierSwitchesRequest(true, false), "boss");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void savingTheShopFormKeepsTheSwitches() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        s.updateCarriers(new CarrierSwitchesRequest(false, true), "boss");
        s.update(req("Quini Bear", "0901234567", hcmIds()), "boss");
        assertThat(s.carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
        assertThat(service(GHN_ON, GHTK_ON).carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
    }

    @Test
    void shopFormSavedFirstLeavesTheSwitchesOn() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        s.update(req("Quini Bear", "0901234567", hcmIds()), "boss");   // creates the row without touching the switches
        assertThat(service(GHN_ON, GHTK_ON).carrierSwitches()).isEqualTo(CarrierSwitches.ALL_ON);
    }

    @Test
    void missingSwitchValueIsInvalidInput() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        assertInvalid(() -> s.updateCarriers(new CarrierSwitchesRequest(null, true), "boss"));
        assertInvalid(() -> s.updateCarriers(null, "boss"));
    }
}
