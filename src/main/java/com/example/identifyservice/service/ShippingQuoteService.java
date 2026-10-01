package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghtk.GhtkFeeRequest;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkGateway;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Computes the shipping fee of a cart: GHTK when configured and it can deliver, otherwise the fixed province table
 * (flagged as an estimate). Weight and value are always computed here from database data, never from the client.
 * <p>
 * A global circuit breaker skips GHTK for {@link #BREAKER_OPEN} after any failure (timeout, unavailable, malformed,
 * HTTP error); a clean refusal (success=false / delivery=false) is not a failure.
 */
@Service
@Slf4j
public class ShippingQuoteService {
    static final String MSG_UNSUPPORTED = "GHTK không hỗ trợ giao tới địa chỉ này";
    static final String MSG_UNSUPPORTED_FALLBACK = MSG_UNSUPPORTED + ", dùng phí tạm tính";
    static final String MSG_DOWN_FALLBACK = "Không kết nối được GHTK, dùng phí tạm tính";
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final Duration BREAKER_OPEN = Duration.ofSeconds(60);
    static final int CACHE_MAX_ENTRIES = 500;
    static final long VALUE_BUCKET = 100_000;

    private record CacheEntry(ShippingQuote quote, Instant expiresAt) {
    }

    private final GhtkGateway ghtk;
    private final GhtkProperties props;
    private final ShippingService shippingService;
    private final Clock clock;
    private final CartMeasurer cartMeasurer;
    /** Insertion-ordered; every entry has the same TTL so the eldest is always the first to expire. */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();
    /** Global circuit breaker: GHTK is skipped until this instant after a failure (null = closed). */
    private Instant ghtkOpenUntil;
    /** True while the single half-open probe call is in flight. */
    private boolean probing;

    public ShippingQuoteService(GhtkGateway ghtk, GhtkProperties props, ShippingService shippingService, Clock clock,
                                CartMeasurer cartMeasurer) {
        this.ghtk = ghtk;
        this.props = props;
        this.shippingService = shippingService;
        this.clock = clock;
        this.cartMeasurer = cartMeasurer;
    }

    /**
     * Quote for the signed-in user's current cart. Deliberately not transactional: the cart is measured in a short
     * transaction and the GHTK call happens with no DB connection held.
     */
    public ShippingQuote quoteCurrentCart(String province, String ward, String address) {
        return quote(cartMeasurer.measureCurrentCart(), province, ward, address);
    }

    public ShippingQuote quote(Cart cart, String province, String ward, String address) {
        return quote(CartMeasure.of(cart), province, ward, address);
    }

    public ShippingQuote quote(CartMeasure measure, String province, String ward, String address) {
        int weightGrams = measure.weightGrams();
        String safeAddress = address == null ? "" : address.trim();

        if (!props.isEnabled()) return tableQuote(province, weightGrams, null);

        String key = cacheKey(province, ward, safeAddress, weightGrams, measure.value());
        ShippingQuote cached = cacheGet(key);
        if (cached != null) return cached;

        if (!allowGhtkCall()) return tableQuote(province, weightGrams, MSG_DOWN_FALLBACK);

        GhtkFeeResult result;
        try {
            result = ghtk.calculateFee(
                    new GhtkFeeRequest(province.trim(), ward.trim(), safeAddress, weightGrams, measure.value()));
        } catch (RuntimeException e) {
            if (!(e instanceof GhtkUnavailableException)) log.warn("Unexpected GHTK quote failure: {}", e.toString());
            recordFailure();
            return tableQuote(province, weightGrams, MSG_DOWN_FALLBACK);
        }
        recordSuccess(); // a clean answer (even a refusal) means GHTK is reachable
        if (result.success() && result.deliverable()) {
            ShippingQuote quote = new ShippingQuote(result.fee(), ShippingSource.GHTK, false, weightGrams, true, null);
            cachePut(key, quote);
            return quote;
        }
        return unsupportedQuote(province, weightGrams);
    }

    /** GHTK refused the address: use the table if it knows the province, otherwise report not deliverable. */
    private ShippingQuote unsupportedQuote(String province, int weightGrams) {
        return shippingService.findRate(province)
                .map(rate -> new ShippingQuote(rate.getFee(), ShippingSource.TABLE, true, weightGrams, true,
                        MSG_UNSUPPORTED_FALLBACK))
                .orElseGet(() -> new ShippingQuote(0, ShippingSource.GHTK, false, weightGrams, false, MSG_UNSUPPORTED));
    }

    /**
     * Throws INVALID_PROVINCE itself. It uses the non-throwing lookup on purpose: an AppException crossing a
     * transactional proxy would mark the caller's outer transaction rollback-only.
     */
    private ShippingQuote tableQuote(String province, int weightGrams, String message) {
        long fee = shippingService.findRate(province)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_PROVINCE)).getFee();
        return new ShippingQuote(fee, ShippingSource.TABLE, true, weightGrams, true, message);
    }

    private synchronized boolean allowGhtkCall() {
        if (ghtkOpenUntil == null) return true;
        if (clock.instant().isBefore(ghtkOpenUntil)) return false;
        if (probing) return false;
        probing = true;
        return true;
    }

    private synchronized void recordFailure() {
        ghtkOpenUntil = clock.instant().plus(BREAKER_OPEN);
        probing = false;
    }

    private synchronized void recordSuccess() {
        ghtkOpenUntil = null;
        probing = false;
    }

    private static String cacheKey(String province, String ward, String address, int weight, long value) {
        return normalize(province) + "|" + normalize(ward) + "|" + normalize(address) + "|" + weight + "|"
                + (value / VALUE_BUCKET * VALUE_BUCKET);
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

    /** Clears the quote cache and closes the circuit breaker (used by tests). */
    public synchronized void clearCache() {
        cache.clear();
        ghtkOpenUntil = null;
        probing = false;
    }
}
