package com.example.identifyservice.service;

/**
 * The shop's pickup address: GHN ids and names (GHN mode) or names only (text mode). Immutable; also the JSON shape
 * of the {@code pickup} object of the admin settings API.
 */
public record PickupAddress(Integer provinceId, Integer districtId, String wardCode, String provinceName,
                            String districtName, String wardName, String address) {
    public static final PickupAddress EMPTY = new PickupAddress(null, null, null, null, null, null, null);

    /** Both GHN origin fields are present, so GHN can price from this address. */
    public boolean hasGhnOrigin() {
        return districtId != null && wardCode != null && !wardCode.isBlank();
    }

    /** Province and ward names are present, so GHTK can pick up from this address. */
    public boolean hasNamedOrigin() {
        return provinceName != null && !provinceName.isBlank() && wardName != null && !wardName.isBlank();
    }

    /** Stable text of the fields that influence a quote, used in cache keys. */
    public String signature() {
        return districtId + "/" + wardCode + "/" + provinceName + "/" + districtName + "/" + wardName + "/" + address;
    }
}
