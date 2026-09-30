package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.ShippingRate;

public record ShippingRateResponse(String id, String province, long fee) {
    public static ShippingRateResponse from(ShippingRate r) {
        return new ShippingRateResponse(r.getId(), r.getProvince(), r.getFee());
    }
}
