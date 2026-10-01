package com.example.identifyservice.ghn;

/** Destination and parcel of a fee query; the shop side and parcel dimensions come from {@link GhnProperties}. */
public record GhnFeeRequest(int toDistrictId, String toWardCode, int weightGrams, long insuranceValue) {
}
