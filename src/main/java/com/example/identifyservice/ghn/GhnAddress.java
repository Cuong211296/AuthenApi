package com.example.identifyservice.ghn;

/** Province, district and ward names resolved from GHN master data. */
public record GhnAddress(String provinceName, String districtName, String wardName) {
}
