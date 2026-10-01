package com.example.identifyservice.ghn;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cached GHN address master data (provinces, districts per province, wards per district) and id to name
 * resolution. Entries live 24 h; when a refresh fails the stale entry keeps being served (address names rarely
 * change). After a failed fetch GHN is considered down for {@link #DOWN_MEMORY} so a dead GHN does not make every
 * request wait for a timeout. No lock is ever held while calling GHN.
 */
@Service
public class GhnMasterDataService {
    static final Duration TTL = Duration.ofHours(24);
    static final Duration DOWN_MEMORY = Duration.ofSeconds(30);
    static final int MAX_DISTRICT_ENTRIES = 200;
    static final int MAX_WARD_ENTRIES = 1_000;

    /** Outcome of {@link #resolve}: RESOLVED carries the names, INVALID means unknown or inconsistent ids. */
    public record Resolution(Status status, GhnAddress address) {
        public enum Status {RESOLVED, INVALID, UNAVAILABLE}

        static Resolution resolved(GhnAddress address) {
            return new Resolution(Status.RESOLVED, address);
        }

        static Resolution invalid() {
            return new Resolution(Status.INVALID, null);
        }

        static Resolution unavailable() {
            return new Resolution(Status.UNAVAILABLE, null);
        }
    }

    private record Entry<V>(V value, Instant expiresAt) {
    }

    /** Insertion-ordered, bounded; the eldest entry is evicted first. */
    private static final class Cache<K, V> {
        private final int max;
        private final Map<K, Entry<V>> map = new LinkedHashMap<>();

        Cache(int max) {
            this.max = max;
        }

        synchronized Entry<V> get(K key) {
            return map.get(key);
        }

        synchronized void put(K key, Entry<V> entry) {
            map.remove(key);
            while (map.size() >= max) {
                var it = map.keySet().iterator();
                it.next();
                it.remove();
            }
            map.put(key, entry);
        }

        synchronized void clear() {
            map.clear();
        }
    }

    private static final String PROVINCES_KEY = "all";

    private final GhnGateway gateway;
    private final GhnProperties props;
    private final Clock clock;
    private final Cache<String, List<GhnProvince>> provinces = new Cache<>(1);
    private final Cache<Integer, List<GhnDistrict>> districts = new Cache<>(MAX_DISTRICT_ENTRIES);
    private final Cache<Integer, List<GhnWard>> wards = new Cache<>(MAX_WARD_ENTRIES);
    private volatile Instant downUntil;

    public GhnMasterDataService(GhnGateway gateway, GhnProperties props, Clock clock) {
        this.gateway = gateway;
        this.props = props;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** True when GHN is configured and its province list can be had (cached or fetched). Never throws. */
    public boolean isAvailable() {
        if (!props.isEnabled()) return false;
        try {
            return !provinces().isEmpty();
        } catch (GhnUnavailableException e) {
            return false;
        }
    }

    /** @throws GhnUnavailableException when GHN is disabled or down and nothing is cached */
    public List<GhnProvince> provinces() {
        return load(provinces, PROVINCES_KEY, () -> {
            List<GhnProvince> list = gateway.provinces();
            if (list.isEmpty()) throw new GhnUnavailableException("GHN returned no provinces");
            return List.copyOf(list);
        });
    }

    /** Districts of a province; empty (without calling GHN) when the province id is unknown. */
    public List<GhnDistrict> districts(int provinceId) {
        if (findProvince(provinceId).isEmpty()) return List.of();
        return load(districts, provinceId, () -> List.copyOf(gateway.districts(provinceId)));
    }

    public List<GhnWard> wards(int districtId) {
        return load(wards, districtId, () -> List.copyOf(gateway.wards(districtId)));
    }

    /**
     * Checks that the province exists, the district belongs to it and the ward belongs to the district, and
     * returns their names from master data.
     */
    public Resolution resolve(Integer provinceId, Integer districtId, String wardCode) {
        if (!props.isEnabled()) return Resolution.unavailable();
        if (provinceId == null || districtId == null || wardCode == null || wardCode.isBlank())
            return Resolution.invalid();
        String code = wardCode.trim();
        try {
            Optional<GhnProvince> province = findProvince(provinceId);
            if (province.isEmpty()) return Resolution.invalid();
            Optional<GhnDistrict> district = districts(provinceId).stream()
                    .filter(d -> d.id() == districtId && d.provinceId() == provinceId).findFirst();
            if (district.isEmpty()) return Resolution.invalid();
            Optional<GhnWard> ward = wards(districtId).stream()
                    .filter(w -> w.code().equals(code) && w.districtId() == districtId).findFirst();
            if (ward.isEmpty()) return Resolution.invalid();
            return Resolution.resolved(new GhnAddress(province.get().name(), district.get().name(), ward.get().name()));
        } catch (GhnUnavailableException e) {
            return Resolution.unavailable();
        }
    }

    /** Drops every cache and the outage memory (used by tests). */
    public void clear() {
        provinces.clear();
        districts.clear();
        wards.clear();
        downUntil = null;
    }

    private Optional<GhnProvince> findProvince(int id) {
        return provinces().stream().filter(p -> p.id() == id).findFirst();
    }

    private <K, V> V load(Cache<K, V> cache, K key, Supplier<V> fetch) {
        if (!props.isEnabled()) throw new GhnUnavailableException("GHN is not configured");
        Entry<V> entry = cache.get(key);
        Instant now = clock.instant();
        if (entry != null && entry.expiresAt().isAfter(now)) return entry.value();
        Instant down = downUntil;
        if (down != null && now.isBefore(down)) {
            if (entry != null) return entry.value();
            throw new GhnUnavailableException("GHN master data is temporarily down");
        }
        try {
            V value = fetch.get(); // no lock held here
            downUntil = null;
            cache.put(key, new Entry<>(value, clock.instant().plus(TTL)));
            return value;
        } catch (RuntimeException e) {
            downUntil = clock.instant().plus(DOWN_MEMORY);
            if (entry != null) return entry.value();
            if (e instanceof GhnUnavailableException u) throw u;
            throw new GhnUnavailableException("GHN master data call failed: " + e.getClass().getSimpleName());
        }
    }
}
