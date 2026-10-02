package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.ShippingRate;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnAddress;
import com.example.identifyservice.ghn.GhnFeeRequest;
import com.example.identifyservice.ghn.GhnFeeResult;
import com.example.identifyservice.ghn.GhnGateway;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnMasterDataService.Resolution;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghn.GhnRejectedException;
import com.example.identifyservice.ghn.GhnUnavailableException;
import com.example.identifyservice.ghtk.GhtkFeeRequest;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkGateway;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Computes the shipping fee of a cart. Every available carrier (GHN when the address carries GHN ids, GHTK; each
 * configured in the environment and switched on by the admin) is asked in parallel and the cheaper fee is the default;
 * with none available the fixed province table is used (flagged as an estimate). Weight and value are always
 * computed here from database data, never from the client, and GHN ids are validated against GHN master data.
 * <p>
 * Each carrier has its own circuit breaker ({@link CarrierBreaker}) that skips it for 60 s after any failure
 * (timeout, unavailable, malformed, HTTP error); a clean refusal (GHTK success=false / delivery=false, GHN HTTP 400)
 * is not a failure. Quotes are cached for 10 minutes per carrier.
 */
@Service
@Slf4j
public class ShippingQuoteService {
    static final String MSG_UNSUPPORTED = "GHTK không hỗ trợ giao tới địa chỉ này";
    static final String MSG_UNSUPPORTED_FALLBACK = MSG_UNSUPPORTED + ", dùng phí tạm tính";
    static final String MSG_DOWN_FALLBACK = "Không kết nối được GHTK, dùng phí tạm tính";
    static final String MSG_GHN_DOWN_FALLBACK = "Không kết nối được GHN, dùng phí tạm tính";
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final Duration BREAKER_OPEN = CarrierBreaker.OPEN_FOR;
    static final int CACHE_MAX_ENTRIES = 500;
    static final long VALUE_BUCKET = 100_000;
    static final long MAX_INSURANCE_VALUE = 5_000_000;

    /** Which carrier serves quotes and how the checkout address is entered (for {@code GET /shipping/config}). */
    public record ProviderConfig(String provider, String addressMode) {
    }

    /** Every live quote (cheapest first, GHN first on a tie) and the default selection; options is empty on a fallback. */
    public record QuoteOptions(ShippingQuote selected, List<ShippingQuote> options) {
    }

    private enum GhtkOutcome { QUOTED, REFUSED, DOWN }

    private record GhtkAttempt(GhtkOutcome outcome, ShippingQuote quote) {
    }

    /** Small daemon pool so the two carriers are asked at the same time (waits do not add up). */
    private static final ExecutorService CARRIER_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "carrier-quote");
        t.setDaemon(true);
        return t;
    });

    private record CacheEntry(ShippingQuote quote, Instant expiresAt) {
    }

    private final GhtkGateway ghtk;
    private final GhtkProperties props;
    private final GhnGateway ghn;
    private final GhnProperties ghnProps;
    private final GhnMasterDataService masterData;
    private final ShippingService shippingService;
    private final Clock clock;
    private final CartMeasurer cartMeasurer;
    /** Null only in tests that do not care about settings: the pickup is then always empty (env behaviour). */
    private final ShopSettingsService settings;
    /** Insertion-ordered; every entry has the same TTL so the eldest is always the first to expire. */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();
    private final CarrierBreaker ghtkBreaker;
    private final CarrierBreaker ghnBreaker;

    public ShippingQuoteService(GhtkGateway ghtk, GhtkProperties props, GhnGateway ghn, GhnProperties ghnProps,
                                GhnMasterDataService masterData, ShippingService shippingService, Clock clock,
                                CartMeasurer cartMeasurer) {
        this(ghtk, props, ghn, ghnProps, masterData, shippingService, clock, cartMeasurer, null);
    }

    @Autowired
    public ShippingQuoteService(GhtkGateway ghtk, GhtkProperties props, GhnGateway ghn, GhnProperties ghnProps,
                                GhnMasterDataService masterData, ShippingService shippingService, Clock clock,
                                CartMeasurer cartMeasurer, ShopSettingsService settings) {
        this.ghtk = ghtk;
        this.props = props;
        this.ghn = ghn;
        this.ghnProps = ghnProps;
        this.masterData = masterData;
        this.shippingService = shippingService;
        this.clock = clock;
        this.cartMeasurer = cartMeasurer;
        this.settings = settings;
        this.ghtkBreaker = new CarrierBreaker(clock);
        this.ghnBreaker = new CarrierBreaker(clock);
        // a changed pickup must never be answered with a quote priced from the old one
        if (settings != null) settings.addChangeListener(this::clearQuoteCache);
    }

    /** The shop pickup address from the in-memory settings snapshot (no database access per quote). */
    private PickupAddress pickup() {
        return settings == null ? PickupAddress.EMPTY : settings.pickup();
    }

    private boolean ghtkEnabled(PickupAddress pickup) {
        return props.isEnabled(pickup.provinceName(), pickup.wardName());
    }

    private CarrierSwitches switches() {
        return settings == null ? CarrierSwitches.ALL_ON : settings.carrierSwitches();
    }

    /** GHN quotes fees only when configured and switched on. */
    private boolean ghnQuoting() {
        return ghnProps.isEnabled() && switches().ghn();
    }

    private boolean ghtkQuoting(PickupAddress pickup) {
        return ghtkEnabled(pickup) && switches().ghtk();
    }

    /**
     * Provider and address mode for the storefront: GHN id selects only while GHN master data is reachable. The
     * switches only decide who quotes fees, never how the address is entered.
     */
    public ProviderConfig providerConfig() {
        String mode = masterData.isAvailable() ? "GHN_IDS" : "TEXT";
        if (ghnQuoting() && masterData.isAvailable()) return new ProviderConfig("GHN", mode);
        return new ProviderConfig(ghtkQuoting(pickup()) ? "GHTK" : "TABLE", mode);
    }

    /**
     * Quote for the signed-in user's current cart. Deliberately not transactional: the cart is measured in a short
     * transaction and the carrier calls happen with no DB connection held.
     */
    public ShippingQuote quoteCurrentCart(QuoteAddress address) {
        requireAddressShape(address);
        return quote(cartMeasurer.measureCurrentCart(), address);
    }

    public QuoteOptions quoteOptionsForCurrentCart(QuoteAddress address) {
        requireAddressShape(address);
        return quoteOptionsResolved(cartMeasurer.measureCurrentCart(), resolveAddress(address));
    }

    public ShippingQuote quote(Cart cart, String province, String ward, String address) {
        return quote(CartMeasure.of(cart), QuoteAddress.text(province, ward, address));
    }

    public ShippingQuote quote(Cart cart, QuoteAddress address) {
        return quote(CartMeasure.of(cart), address);
    }

    public ShippingQuote quote(CartMeasure measure, QuoteAddress address) {
        return quoteResolved(measure, resolveAddress(address));
    }

    /**
     * Validates the address and, in GHN id mode, replaces the client's names with the ones from GHN master data
     * (never trusting client names). Unknown or inconsistent ids are INVALID_INPUT. When GHN master data is down the
     * text names are used if present (ids dropped), otherwise SHIPPING_PROVIDER_UNAVAILABLE. Never throws through a
     * transactional proxy, so it is safe to call inside the checkout transaction.
     */
    public QuoteAddress resolveAddress(QuoteAddress address) {
        requireAddressShape(address);
        if (!address.hasGhnIds()) return address.withoutIds();
        if (!ghnProps.isEnabled()) {
            if (!address.hasTextNames()) throw new AppException(ErrorCode.INVALID_INPUT);
            return address.withoutIds();
        }
        Resolution r = masterData.resolve(address.provinceId(), address.districtId(), address.wardCode());
        switch (r.status()) {
            case INVALID:
                throw new AppException(ErrorCode.INVALID_INPUT);
            case UNAVAILABLE:
                if (!address.hasTextNames()) throw new AppException(ErrorCode.SHIPPING_PROVIDER_UNAVAILABLE);
                return address.withoutIds();
            default:
                GhnAddress names = r.address();
                return new QuoteAddress(names.provinceName(), names.districtName(), names.wardName(),
                        address.address(), address.provinceId(), address.districtId(), address.wardCode().trim());
        }
    }

    /** Either GHN ids (all three) or text province and ward must be present. */
    public static void requireAddressShape(QuoteAddress a) {
        if (!a.hasGhnIds() && !a.hasTextNames()) throw new AppException(ErrorCode.INVALID_INPUT);
    }

    /** The default selection for an address already returned by {@link #resolveAddress}. */
    public ShippingQuote quoteResolved(CartMeasure measure, QuoteAddress address) {
        return quoteOptionsResolved(measure, address).selected();
    }

    /** The quote of the carrier the customer chose (null = the default). A carrier that no longer quotes is rejected. */
    public ShippingQuote quoteForCarrier(CartMeasure measure, QuoteAddress address, ShippingSource carrier) {
        QuoteOptions result = quoteOptionsResolved(measure, address);
        if (carrier == null) return result.selected();
        return result.options().stream().filter(q -> q.source() == carrier).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.SHIPPING_CARRIER_UNAVAILABLE));
    }

    /**
     * Asks every available carrier (GHN on this thread, GHTK on the pool) and returns the live quotes cheapest first.
     * With none, the fallback of the old chain: GHTK refused -> table or not deliverable, GHTK down -> table with the
     * GHTK message, otherwise the table (with the GHN message when GHN was tried).
     */
    public QuoteOptions quoteOptionsResolved(CartMeasure measure, QuoteAddress address) {
        int weightGrams = measure.weightGrams();
        PickupAddress pickup = pickup();
        boolean tryGhn = ghnQuoting() && address.hasGhnIds();
        boolean tryGhtk = ghtkQuoting(pickup) && address.hasTextNames();

        CompletableFuture<GhtkAttempt> ghtkFuture = tryGhtk
                ? CompletableFuture.supplyAsync(() -> ghtkAttempt(measure, address, pickup), CARRIER_POOL) : null;
        ShippingQuote ghnQuote = tryGhn ? ghnQuote(measure, address, pickup) : null;
        GhtkAttempt ghtkAttempt = ghtkFuture == null ? null : ghtkFuture.join();

        List<ShippingQuote> live = new ArrayList<>();
        if (ghnQuote != null) live.add(ghnQuote);
        if (ghtkAttempt != null && ghtkAttempt.outcome() == GhtkOutcome.QUOTED) live.add(ghtkAttempt.quote());
        live.sort(Comparator.comparingLong(ShippingQuote::fee));      // stable: GHN stays ahead on a tie
        if (!live.isEmpty()) return new QuoteOptions(live.get(0), List.copyOf(live));

        if (ghtkAttempt != null) {
            ShippingQuote fallback = ghtkAttempt.outcome() == GhtkOutcome.REFUSED
                    ? unsupportedQuote(address, weightGrams)
                    : tableQuote(address, weightGrams, MSG_DOWN_FALLBACK);
            return new QuoteOptions(fallback, List.of());
        }
        return new QuoteOptions(tableQuote(address, weightGrams, tryGhn ? MSG_GHN_DOWN_FALLBACK : null), List.of());
    }

    /** One GHTK fee attempt (cache, breaker and call exactly as before); never throws. */
    private GhtkAttempt ghtkAttempt(CartMeasure measure, QuoteAddress address, PickupAddress pickup) {
        int weightGrams = measure.weightGrams();
        String province = address.provinceName().trim();
        String ward = address.wardName().trim();
        String safeAddress = address.address() == null ? "" : address.address().trim();
        // the pick-up is wholly from the settings (when they name province and ward) or wholly from the environment
        boolean fromSettings = pickup.hasNamedOrigin();
        String pickProvince = fromSettings ? pickup.provinceName().trim() : props.pickProvince();
        String pickWard = fromSettings ? pickup.wardName().trim() : props.pickWard();
        String pickDistrict = fromSettings ? pickup.districtName() : props.pickDistrict();
        String pickAddress = fromSettings ? pickup.address() : props.pickAddress();
        String pickSignature = normalize(pickProvince) + "/" + normalize(pickDistrict) + "/" + normalize(pickWard)
                + "/" + normalize(pickAddress);
        String key = ghtkCacheKey(pickSignature, province, ward, safeAddress, weightGrams, measure.value());
        ShippingQuote cached = cacheGet(key);
        if (cached != null) return new GhtkAttempt(GhtkOutcome.QUOTED, cached);

        CarrierBreaker.Permit permit = ghtkBreaker.acquire();
        if (permit == CarrierBreaker.Permit.DENIED) return new GhtkAttempt(GhtkOutcome.DOWN, null);

        GhtkFeeResult result;
        try {
            result = ghtk.calculateFee(new GhtkFeeRequest(province, ward, safeAddress, weightGrams, measure.value(),
                    pickProvince, pickWard, pickDistrict, pickAddress));
            ghtkBreaker.success(); // a clean answer (even a refusal) means GHTK is reachable
        } catch (RuntimeException e) {
            if (!(e instanceof GhtkUnavailableException))
                log.warn("Unexpected GHTK quote failure: {}", e.getClass().getSimpleName());
            ghtkBreaker.failure();
            return new GhtkAttempt(GhtkOutcome.DOWN, null);
        } finally {
            ghtkBreaker.release(permit);
        }
        if (result.success() && result.deliverable()) {
            ShippingQuote quote = new ShippingQuote(result.fee(), ShippingSource.GHTK, false, weightGrams, true, null);
            cachePut(key, quote);
            return new GhtkAttempt(GhtkOutcome.QUOTED, quote);
        }
        return new GhtkAttempt(GhtkOutcome.REFUSED, null);
    }

    /** GHN fee, or null when GHN cannot answer (down, breaker open, refused): the caller then falls back. */
    private ShippingQuote ghnQuote(CartMeasure measure, QuoteAddress address, PickupAddress pickup) {
        int weightGrams = measure.weightGrams();
        // both from_district_id and from_ward_code or neither: GHN ignores a lone from_district_id
        Integer fromDistrict = pickup.hasGhnOrigin() ? pickup.districtId() : null;
        String fromWard = pickup.hasGhnOrigin() ? pickup.wardCode().trim() : null;
        String key = ghnCacheKey(fromDistrict, fromWard, address.districtId(), address.wardCode().trim(), weightGrams,
                measure.value());
        ShippingQuote cached = cacheGet(key);
        if (cached != null) return cached;

        CarrierBreaker.Permit permit = ghnBreaker.acquire();
        if (permit == CarrierBreaker.Permit.DENIED) return null;
        try {
            GhnFeeResult result = ghn.calculateFee(new GhnFeeRequest(address.districtId(),
                    address.wardCode().trim(), weightGrams, Math.min(measure.value(), MAX_INSURANCE_VALUE),
                    fromDistrict, fromWard));
            if (result == null || result.total() < 0) {
                ghnBreaker.failure();
                return null;
            }
            ghnBreaker.success();
            ShippingQuote quote = new ShippingQuote(result.total(), ShippingSource.GHN, false, weightGrams, true, null);
            cachePut(key, quote);
            return quote;
        } catch (GhnRejectedException e) {
            ghnBreaker.success(); // GHN answered, it just refuses this parcel or route
            return null;
        } catch (RuntimeException e) {
            if (!(e instanceof GhnUnavailableException))
                log.warn("Unexpected GHN quote failure: {}", e.getClass().getSimpleName());
            ghnBreaker.failure();
            return null;
        } finally {
            ghnBreaker.release(permit);
        }
    }

    /** GHTK refused the address: use the table if it knows the province, otherwise report not deliverable. */
    private ShippingQuote unsupportedQuote(QuoteAddress address, int weightGrams) {
        return findRate(address)
                .map(rate -> new ShippingQuote(rate.getFee(), ShippingSource.TABLE, true, weightGrams, true,
                        MSG_UNSUPPORTED_FALLBACK))
                .orElseGet(() -> new ShippingQuote(0, ShippingSource.GHTK, false, weightGrams, false, MSG_UNSUPPORTED));
    }

    /**
     * The fixed-table fallback. It uses the non-throwing lookup on purpose: an AppException crossing a
     * transactional proxy would mark the caller's outer transaction rollback-only. A province missing from the table
     * is INVALID_PROVINCE for a text address and SHIPPING_NOT_AVAILABLE for a GHN-validated one.
     */
    private ShippingQuote tableQuote(QuoteAddress address, int weightGrams, String message) {
        long fee = findRate(address)
                .orElseThrow(() -> new AppException(address.hasGhnIds()
                        ? ErrorCode.SHIPPING_NOT_AVAILABLE : ErrorCode.INVALID_PROVINCE)).getFee();
        return new ShippingQuote(fee, ShippingSource.TABLE, true, weightGrams, true, message);
    }

    private Optional<ShippingRate> findRate(QuoteAddress address) {
        return address.hasGhnIds()
                ? shippingService.findRateByCarrierName(address.provinceName())
                : shippingService.findRate(address.provinceName());
    }

    private static String ghtkCacheKey(String pick, String province, String ward, String address, int weight,
                                       long value) {
        return "ghtk|" + pick + "|" + normalize(province) + "|" + normalize(ward) + "|" + normalize(address) + "|" + weight + "|"
                + bucket(value);
    }

    private static String ghnCacheKey(Integer fromDistrict, String fromWard, int districtId, String wardCode,
                                      int weight, long value) {
        return "ghn|" + (fromDistrict == null ? "env" : fromDistrict + "/" + normalize(fromWard)) + "|" + districtId + "|" + normalize(wardCode) + "|" + weight + "|" + bucket(value);
    }

    private static long bucket(long value) {
        return value / VALUE_BUCKET * VALUE_BUCKET;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private synchronized ShippingQuote cacheGet(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (!entry.expiresAt().isAfter(clock.instant())) {
            cache.remove(key);
            return null;
        }
        return entry.quote();
    }

    private synchronized void cachePut(String key, ShippingQuote quote) {
        Instant now = clock.instant();
        cache.remove(key);
        for (Iterator<CacheEntry> it = cache.values().iterator(); it.hasNext(); ) {
            if (it.next().expiresAt().isAfter(now)) break;
            it.remove();
        }
        while (cache.size() >= CACHE_MAX_ENTRIES) {
            Iterator<String> it = cache.keySet().iterator();
            it.next();
            it.remove();
        }
        cache.put(key, new CacheEntry(quote, now.plus(CACHE_TTL)));
    }

    /**
     * Drops the cached quotes only (called after the shop settings change). Circuit-breaker state and GHN master
     * data are left alone: a settings edit says nothing about whether a carrier is reachable.
     */
    public void clearQuoteCache() {
        synchronized (this) {
            cache.clear();
        }
    }

    /** Clears the quote cache and GHN master data, and closes both circuit breakers (used by tests). */
    public void clearCache() {
        synchronized (this) {
            cache.clear();
        }
        ghtkBreaker.reset();
        ghnBreaker.reset();
        masterData.clear();
    }
}
