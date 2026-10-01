package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.ShopSettingsRequest;
import com.example.identifyservice.dto.response.ShopSettingsResponse;
import com.example.identifyservice.entity.ShopSettings;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnAddress;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnMasterDataService.Resolution;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.repository.ShopSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * The shop's editable settings (name, phone, pickup address). GHN has no "update shop" API, so the pickup lives in
 * our own database and is sent with every fee request.
 * <p>
 * The quote path reads {@link #pickup()} from an in-memory snapshot (loaded once, replaced on every update), so it
 * never queries the database per request. {@link #update} validates (and, in GHN mode, names the address through GHN
 * master data) before touching the database, with no transaction and no lock open during that HTTP; only the short
 * save-and-publish step is serialised. Not {@code @Transactional}: an AppException therefore never crosses a
 * transactional proxy.
 */
@Service
@Slf4j
public class ShopSettingsService {
    static final String ID = "main";
    static final Pattern PHONE = Pattern.compile("^(0|\\+84)[0-9]{9}$");

    private final ShopSettingsRepository repository;
    private final GhnMasterDataService masterData;
    private final GhnProperties ghnProps;
    private final GhtkProperties ghtkProps;
    private final Clock clock;
    private final AtomicReference<PickupAddress> snapshot = new AtomicReference<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final Object writeLock = new Object();

    public ShopSettingsService(ShopSettingsRepository repository, GhnMasterDataService masterData,
                               GhnProperties ghnProps, GhtkProperties ghtkProps, Clock clock) {
        this.repository = repository;
        this.masterData = masterData;
        this.ghnProps = ghnProps;
        this.ghtkProps = ghtkProps;
        this.clock = clock;
    }

    /** Loads the snapshot at startup so the first quote does not have to; a failure just defers it to first use. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            pickup();
        } catch (RuntimeException e) {
            log.warn("Shop settings could not be preloaded: {}", e.getClass().getSimpleName());
        }
    }

    /** Current settings (empty values when nothing was saved yet). Never returns null. */
    @PreAuthorize("hasRole('ADMIN')")
    public ShopSettingsResponse get() {
        return toResponse(repository.findById(ID).orElse(null));
    }

    /**
     * The pickup address for the quote path: an immutable in-memory snapshot, {@link PickupAddress#EMPTY} when
     * nothing was saved. No admin rights needed. Reads the database only once, to load the snapshot.
     */
    public PickupAddress pickup() {
        PickupAddress current = snapshot.get();
        if (current != null) return current;
        PickupAddress loaded = repository.findById(ID).map(ShopSettingsService::toPickup).orElse(PickupAddress.EMPTY);
        snapshot.compareAndSet(null, loaded);   // an update that raced ahead of this load wins
        return snapshot.get();
    }

    /** Runs after every successful update (the quote service clears its cached quotes here). */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /** Forgets the snapshot so the next {@link #pickup()} reloads from the database (tests, manual SQL edits). */
    public void invalidateSnapshot() {
        snapshot.set(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ShopSettingsResponse update(ShopSettingsRequest request, String username) {
        if (request == null) throw invalid();
        String shopName = required(request.shopName(), 100);
        String phone = optional(request.phone(), 20);
        if (phone != null && !PHONE.matcher(phone).matches()) throw invalid();
        PickupAddress pickup = validatePickup(request.pickup());

        synchronized (writeLock) {   // database only: all HTTP happened above
            ShopSettings row = repository.findById(ID).orElseGet(() -> ShopSettings.builder().id(ID).build());
            row.setShopName(shopName);
            row.setPhone(phone);
            row.setProvinceId(pickup.provinceId());
            row.setDistrictId(pickup.districtId());
            row.setWardCode(pickup.wardCode());
            row.setProvinceName(pickup.provinceName());
            row.setDistrictName(pickup.districtName());
            row.setWardName(pickup.wardName());
            row.setAddress(pickup.address());
            row.setUpdatedAt(clock.instant());
            row.setUpdatedBy(username);
            ShopSettings saved = repository.save(row);
            snapshot.set(toPickup(saved));
            changeListeners.forEach(Runnable::run);
            return toResponse(saved);
        }
    }

    private PickupAddress validatePickup(PickupAddress in) {
        if (in == null) throw invalid();
        String address = optional(in.address(), 300);
        if (ghnProps.isEnabled()) {
            String ward = optional(in.wardCode(), 20);
            if (in.provinceId() == null || in.districtId() == null || ward == null) throw invalid();
            Resolution r = masterData.resolve(in.provinceId(), in.districtId(), ward);
            switch (r.status()) {
                case INVALID:
                    throw invalid();
                case UNAVAILABLE:
                    throw new AppException(ErrorCode.SHIPPING_PROVIDER_UNAVAILABLE);
                default:
                    GhnAddress names = r.address();
                    return new PickupAddress(in.provinceId(), in.districtId(), ward, names.provinceName(),
                            names.districtName(), names.wardName(), address);
            }
        }
        if (in.provinceId() != null || in.districtId() != null || optional(in.wardCode(), 20) != null) throw invalid();
        String province = required(in.provinceName(), 100);
        String ward = required(in.wardName(), 100);
        return new PickupAddress(null, null, null, province, optional(in.districtName(), 100), ward, address);
    }

    private ShopSettingsResponse toResponse(ShopSettings row) {
        PickupAddress pickup = row == null ? PickupAddress.EMPTY : toPickup(row);
        String shopId = ghnProps.shopId() == null || ghnProps.shopId().isBlank() ? null : ghnProps.shopId().trim();
        var carriers = new ShopSettingsResponse.Carriers(
                new ShopSettingsResponse.Ghn(ghnProps.isEnabled(), shopId),
                new ShopSettingsResponse.Ghtk(ghtkProps.isEnabled(pickup.provinceName(), pickup.wardName())));
        String mode = ghnProps.isEnabled() ? "GHN_IDS" : "TEXT";
        return new ShopSettingsResponse(row == null ? null : row.getShopName(), row == null ? null : row.getPhone(),
                pickup, carriers, mode, row == null ? null : row.getUpdatedAt(), row == null ? null : row.getUpdatedBy());
    }

    private static PickupAddress toPickup(ShopSettings s) {
        return new PickupAddress(s.getProvinceId(), s.getDistrictId(), s.getWardCode(), s.getProvinceName(),
                s.getDistrictName(), s.getWardName(), s.getAddress());
    }

    /** Trimmed value, or null when blank; INVALID_INPUT when longer than {@code max}. */
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > max) throw invalid();
        return v;
    }

    private static String required(String value, int max) {
        String v = optional(value, max);
        if (v == null) throw invalid();
        return v;
    }

    private static AppException invalid() {
        return new AppException(ErrorCode.INVALID_INPUT);
    }
}
