package com.example.identifyservice.ghtk;

/**
 * Destination and parcel of a fee query. The pick-up fields are the resolved pick-up address (shop settings, else
 * {@link GhtkProperties}); when null the gateway falls back to its properties.
 */
public record GhtkFeeRequest(String province, String ward, String address, int weightGrams, long value,
                             String pickProvince, String pickWard, String pickDistrict, String pickAddress) {
    public GhtkFeeRequest(String province, String ward, String address, int weightGrams, long value) {
        this(province, ward, address, weightGrams, value, null, null, null, null);
    }
}
