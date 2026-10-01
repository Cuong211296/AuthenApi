package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghtk.GhtkFeeRequest;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkGateway;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import com.example.identifyservice.repository.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
@Slf4j
public class ShippingQuoteService {
    static final String MSG_UNSUPPORTED = "GHTK không hỗ trợ giao tới địa chỉ này";
    static final String MSG_UNSUPPORTED_FALLBACK = MSG_UNSUPPORTED + ", dùng phí tạm tính";
    static final String MSG_DOWN_FALLBACK = "Không kết nối được GHTK, dùng phí tạm tính";
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final int CACHE_MAX_ENTRIES = 500;
    static final long VALUE_BUCKET = 100_000;

    private record CacheEntry(ShippingQuote quote, Instant expiresAt) {
    }

    private final GhtkGateway ghtk;
    private final GhtkProperties props;
    private final ShippingService shippingService;
    private final Clock clock;
    private final CartRepository cartRepository;
    private final CurrentUserService currentUserService;
    /** Insertion-ordered; every entry has the same TTL so the eldest is always the first to expire. */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();

    public ShippingQuoteService(GhtkGateway ghtk, GhtkProperties props, ShippingService shippingService, Clock clock,
                                CartRepository cartRepository, CurrentUserService currentUserService) {
        this.ghtk = ghtk;
        this.props = props;
        this.shippingService = shippingService;
        this.clock = clock;
        this.cartRepository = cartRepository;
        this.currentUserService = currentUserService;
    }

    /** Quote for the signed-in user's current cart. */
    @Transactional(readOnly = true)
    public ShippingQuote quoteCurrentCart(String province, String ward, String address) {
        Cart cart = cartRepository.findByUser(currentUserService.requireUser())
                .orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));
        if (cart.getItems().isEmpty()) throw new AppException(ErrorCode.CART_EMPTY);
        return quote(cart, province, ward, address);
    }

    public ShippingQuote quote(Cart cart, String province, String ward, String address) {
        long weight = 0;
        long value = 0;
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = item.getVariant();
            weight += (long) item.getQuantity() * variant.getProduct().effectiveWeight();
            value += variant.effectivePrice() * item.getQuantity();
        }
        int weightGrams = (int) Math.max(1, Math.min(weight, Integer.MAX_VALUE));
        String safeAddress = address == null ? "" : address.trim();

        if (!props.isEnabled()) return tableQuote(province, weightGrams, null);

        String key = cacheKey(province, ward, safeAddress, weightGrams, value);
        ShippingQuote cached = cacheGet(key);
        if (cached != null) return cached;

        String fallbackMessage;
        try {
            GhtkFeeResult result = ghtk.calculateFee(
                    new GhtkFeeRequest(province.trim(), ward.trim(), safeAddress, weightGrams, value));
            if (result.success() && result.deliverable()) {
                ShippingQuote quote = new ShippingQuote(result.fee(), ShippingSource.GHTK, false, weightGrams, true, null);
                cachePut(key, quote);
                return quote;
            }
            return unsupportedQuote(province, weightGrams);
        } catch (GhtkUnavailableException e) {
            fallbackMessage = MSG_DOWN_FALLBACK;
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Unexpected GHTK quote failure: {}", e.toString());
            fallbackMessage = MSG_DOWN_FALLBACK;
        }
        return tableQuote(province, weightGrams, fallbackMessage); // INVALID_PROVINCE propagates for unknown provinces
    }

    /** GHTK refused the address: use the table if it knows the province, otherwise report not deliverable. */
    private ShippingQuote unsupportedQuote(String province, int weightGrams) {
        try {
            return tableQuote(province, weightGrams, MSG_UNSUPPORTED_FALLBACK);
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.INVALID_PROVINCE) throw e;
            return new ShippingQuote(0, ShippingSource.GHTK, false, weightGrams, false, MSG_UNSUPPORTED);
        }
    }

    private ShippingQuote tableQuote(String province, int weightGrams, String message) {
        long fee = shippingService.requireRate(province).getFee();
        return new ShippingQuote(fee, ShippingSource.TABLE, true, weightGrams, true, message);
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

    public synchronized void clearCache() {
        cache.clear();
    }
}
