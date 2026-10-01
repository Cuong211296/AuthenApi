package com.example.identifyservice.ghtk;

/** Destination and parcel of a fee query; the pick-up side comes from {@link GhtkProperties}. */
public record GhtkFeeRequest(String province, String ward, String address, int weightGrams, long value) {
}
