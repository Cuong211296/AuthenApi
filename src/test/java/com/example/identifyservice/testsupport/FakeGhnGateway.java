package com.example.identifyservice.testsupport;

import com.example.identifyservice.ghn.GhnDistrict;
import com.example.identifyservice.ghn.GhnFeeRequest;
import com.example.identifyservice.ghn.GhnFeeResult;
import com.example.identifyservice.ghn.GhnGateway;
import com.example.identifyservice.ghn.GhnProvince;
import com.example.identifyservice.ghn.GhnUnavailableException;
import com.example.identifyservice.ghn.GhnWard;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Replaces the real GHN gateway in every Spring test. By default it behaves like an unreachable GHN (fee and master
 * data), so tests that do not care about GHN keep using the text address path. {@link #useSampleData()} serves a
 * small consistent address tree. Call {@link #reset()} in both @BeforeEach and @AfterEach.
 */
@Component
@Primary
public class FakeGhnGateway implements GhnGateway {
    public static final GhnProvince HANOI = new GhnProvince(201, "Hà Nội");
    public static final GhnProvince HCM = new GhnProvince(202, "Hồ Chí Minh");
    public static final GhnDistrict QUAN_1 = new GhnDistrict(1442, 202, "Quận 1");
    public static final GhnDistrict BA_DINH = new GhnDistrict(1490, 201, "Quận Ba Đình");
    public static final GhnWard BEN_NGHE = new GhnWard("20308", 1442, "Phường Bến Nghé");
    public static final GhnWard PHUC_XA = new GhnWard("1A0101", 1490, "Phường Phúc Xá");

    private static GhnUnavailableException down() {
        return new GhnUnavailableException("fake GHN is down");
    }

    public volatile Function<GhnFeeRequest, GhnFeeResult> feeHandler = r -> {
        throw down();
    };
    public volatile Supplier<List<GhnProvince>> provinceHandler = () -> {
        throw down();
    };
    public volatile Function<Integer, List<GhnDistrict>> districtHandler = id -> {
        throw down();
    };
    public volatile Function<Integer, List<GhnWard>> wardHandler = id -> {
        throw down();
    };
    public final List<GhnFeeRequest> feeCalls = new CopyOnWriteArrayList<>();
    public final AtomicInteger provinceCalls = new AtomicInteger();
    public final AtomicInteger districtCalls = new AtomicInteger();
    public final AtomicInteger wardCalls = new AtomicInteger();

    public void returnFee(long total) {
        feeHandler = r -> new GhnFeeResult(total);
    }

    public void failFee(RuntimeException e) {
        feeHandler = r -> {
            throw e;
        };
    }

    public void useSampleData() {
        provinceHandler = () -> List.of(HANOI, HCM);
        districtHandler = id -> List.of(QUAN_1, BA_DINH).stream().filter(d -> d.provinceId() == id).toList();
        wardHandler = id -> List.of(BEN_NGHE, PHUC_XA).stream().filter(w -> w.districtId() == id).toList();
    }

    public void masterDataDown() {
        provinceHandler = () -> {
            throw down();
        };
        districtHandler = id -> {
            throw down();
        };
        wardHandler = id -> {
            throw down();
        };
    }

    public void reset() {
        feeHandler = r -> {
            throw down();
        };
        masterDataDown();
        feeCalls.clear();
        provinceCalls.set(0);
        districtCalls.set(0);
        wardCalls.set(0);
    }

    @Override
    public GhnFeeResult calculateFee(GhnFeeRequest request) {
        feeCalls.add(request);
        return feeHandler.apply(request);
    }

    @Override
    public List<GhnProvince> provinces() {
        provinceCalls.incrementAndGet();
        return provinceHandler.get();
    }

    @Override
    public List<GhnDistrict> districts(int provinceId) {
        districtCalls.incrementAndGet();
        return districtHandler.apply(provinceId);
    }

    @Override
    public List<GhnWard> wards(int districtId) {
        wardCalls.incrementAndGet();
        return wardHandler.apply(districtId);
    }
}
