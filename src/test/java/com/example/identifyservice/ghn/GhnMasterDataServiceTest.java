package com.example.identifyservice.ghn;

import com.example.identifyservice.ghn.GhnMasterDataService.Resolution;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GhnMasterDataServiceTest {
    static final GhnProperties ON = new GhnProperties("T", "1", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhnProperties OFF = new GhnProperties("", "", "https://ghn.test", null, 2, 25, 20, 10);

    FakeGhnGateway ghn = new FakeGhnGateway();
    MutableClock clock;
    GhnMasterDataService service;

    @BeforeEach
    void setUp() {
        ghn.reset();
        ghn.useSampleData();
        clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        service = new GhnMasterDataService(ghn, ON, clock);
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
    }

    @Test
    void resolvesIdsToNames() {
        Resolution r = service.resolve(202, 1442, "20308");
        assertThat(r.status()).isEqualTo(Resolution.Status.RESOLVED);
        assertThat(r.address()).isEqualTo(new GhnAddress("Hồ Chí Minh", "Quận 1", "Phường Bến Nghé"));
    }

    @Test
    void unknownOrMismatchedIdsAreInvalid() {
        assertThat(service.resolve(999, 1442, "20308").status()).isEqualTo(Resolution.Status.INVALID);
        assertThat(service.resolve(202, 9999, "20308").status()).isEqualTo(Resolution.Status.INVALID);
        assertThat(service.resolve(202, 1442, "NOPE").status()).isEqualTo(Resolution.Status.INVALID);
        // district exists but belongs to another province
        assertThat(service.resolve(201, 1442, "20308").status()).isEqualTo(Resolution.Status.INVALID);
        // ward exists but belongs to another district
        assertThat(service.resolve(202, 1442, "1A0101").status()).isEqualTo(Resolution.Status.INVALID);
        assertThat(service.resolve(null, 1442, "20308").status()).isEqualTo(Resolution.Status.INVALID);
        assertThat(service.resolve(202, 1442, " ").status()).isEqualTo(Resolution.Status.INVALID);
    }

    @Test
    void districtOfAnotherProvinceIsRejectedEvenIfTheGatewayReturnsIt() {
        ghn.districtHandler = id -> List.of(FakeGhnGateway.BA_DINH);   // province 201, asked for 202
        assertThat(service.resolve(202, 1490, "1A0101").status()).isEqualTo(Resolution.Status.INVALID);
    }

    @Test
    void listsAreCachedForTwentyFourHours() {
        service.provinces();
        service.provinces();
        service.districts(202);
        service.districts(202);
        service.wards(1442);
        service.wards(1442);
        service.resolve(202, 1442, "20308");
        assertThat(ghn.provinceCalls).hasValue(1);
        assertThat(ghn.districtCalls).hasValue(1);
        assertThat(ghn.wardCalls).hasValue(1);

        clock.advance(Duration.ofHours(23));
        service.provinces();
        assertThat(ghn.provinceCalls).hasValue(1);

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        service.provinces();
        service.districts(202);
        service.wards(1442);
        assertThat(ghn.provinceCalls).hasValue(2);
        assertThat(ghn.districtCalls).hasValue(2);
        assertThat(ghn.wardCalls).hasValue(2);
    }

    @Test
    void districtsOfAnUnknownProvinceAreEmptyWithoutCallingGhn() {
        assertThat(service.districts(999)).isEmpty();
        assertThat(ghn.districtCalls).hasValue(0);
    }

    @Test
    void anOutageIsReportedAndRememberedBrieflyButNotForever() {
        ghn.masterDataDown();
        GhnMasterDataService fresh = new GhnMasterDataService(ghn, ON, clock);
        assertThat(fresh.isAvailable()).isFalse();
        assertThatThrownBy(fresh::provinces).isInstanceOf(GhnUnavailableException.class);
        assertThat(fresh.resolve(202, 1442, "20308").status()).isEqualTo(Resolution.Status.UNAVAILABLE);
        assertThat(ghn.provinceCalls).hasValue(1);                // the outage is remembered, no hammering

        ghn.useSampleData();
        assertThat(fresh.isAvailable()).isFalse();                // still inside the short outage memory
        clock.advance(Duration.ofSeconds(31));
        assertThat(fresh.isAvailable()).isTrue();
        assertThat(fresh.resolve(202, 1442, "20308").status()).isEqualTo(Resolution.Status.RESOLVED);
    }

    @Test
    void staleDataIsServedWhenTheRefreshFails() {
        service.provinces();
        clock.advance(Duration.ofHours(25));
        ghn.masterDataDown();
        assertThat(service.provinces()).containsExactly(FakeGhnGateway.HANOI, FakeGhnGateway.HCM);
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void disabledNeverCallsGhn() {
        GhnMasterDataService off = new GhnMasterDataService(ghn, OFF, clock);
        assertThat(off.isAvailable()).isFalse();
        assertThatThrownBy(off::provinces).isInstanceOf(GhnUnavailableException.class);
        assertThat(off.resolve(202, 1442, "20308").status()).isEqualTo(Resolution.Status.UNAVAILABLE);
        assertThat(ghn.provinceCalls).hasValue(0);
    }

    @Test
    void cachesAreBounded() {
        ghn.districtHandler = id -> List.of(new GhnDistrict(id * 10, id, "D"));
        ghn.provinceHandler = () -> java.util.stream.IntStream.range(0, 300)
                .mapToObj(i -> new GhnProvince(i, "P" + i)).toList();
        for (int i = 0; i < 300; i++) service.districts(i);
        assertThat(ghn.districtCalls).hasValue(300);
        service.districts(0);                                      // evicted: the cache is bounded below 300
        assertThat(ghn.districtCalls).hasValue(301);
        service.districts(299);                                    // the newest entry is still cached
        assertThat(ghn.districtCalls).hasValue(301);
    }
}
