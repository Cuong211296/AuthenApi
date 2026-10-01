package com.example.identifyservice.service;

/**
 * Destination of a shipping quote. Names are the text address (province / district / ward); the optional GHN ids
 * select the GHN address mode, in which the server replaces the names with the ones from GHN master data.
 */
public record QuoteAddress(String provinceName, String districtName, String wardName, String address,
                           Integer provinceId, Integer districtId, String wardCode) {

    /** Text-only address (no GHN ids). */
    public static QuoteAddress text(String province, String ward, String address) {
        return new QuoteAddress(province, null, ward, address, null, null, null);
    }

    public boolean hasGhnIds() {
        return provinceId != null && districtId != null && wardCode != null && !wardCode.isBlank();
    }

    public boolean hasTextNames() {
        return provinceName != null && !provinceName.isBlank() && wardName != null && !wardName.isBlank();
    }

    QuoteAddress withoutIds() {
        return new QuoteAddress(provinceName, districtName, wardName, address, null, null, null);
    }
}
