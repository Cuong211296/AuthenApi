package com.example.identifyservice.ghn;

/**
 * Destination and parcel of a fee query. {@code fromDistrictId} and {@code fromWardCode} (the shop settings'
 * pickup address) are optional and only sent as a pair; without them the {@link GhnProperties} fallback and the
 * registered shop address apply. Parcel dimensions come from {@link GhnProperties}.
 */
public record GhnFeeRequest(int toDistrictId, String toWardCode, int weightGrams, long insuranceValue,
                            Integer fromDistrictId, String fromWardCode) {
    public GhnFeeRequest(int toDistrictId, String toWardCode, int weightGrams, long insuranceValue) {
        this(toDistrictId, toWardCode, weightGrams, insuranceValue, null, null);
    }

    public boolean hasFromPair() {
        return fromDistrictId != null && fromWardCode != null && !fromWardCode.isBlank();
    }
}
