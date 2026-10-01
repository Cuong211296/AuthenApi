package com.example.identifyservice.dto.request;

import com.example.identifyservice.service.QuoteAddress;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Either {@code province} + {@code ward} (text mode) or the GHN ids {@code provinceId} + {@code districtId} +
 * {@code wardCode} (GHN mode, where the server derives every name) must be present; the cross-field rule is
 * enforced by the quote service (INVALID_INPUT).
 */
public record ShippingQuoteRequest(
        @Size(max = 100, message = "INVALID_INPUT") String province,
        @Size(max = 100, message = "INVALID_INPUT") String ward,
        @Size(max = 300, message = "INVALID_INPUT") String address,
        @Positive(message = "INVALID_INPUT") Integer provinceId,
        @Positive(message = "INVALID_INPUT") Integer districtId,
        @Size(max = 20, message = "INVALID_INPUT") String wardCode,
        @Size(max = 100, message = "INVALID_INPUT") String district) {

    public QuoteAddress toAddress() {
        return new QuoteAddress(province, district, ward, address, provinceId, districtId, wardCode);
    }
}
