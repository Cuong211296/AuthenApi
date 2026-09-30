package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.ProductVariant;

public record VariantResponse(String id, String size, String color, String sku, int stock, long price, boolean active) {
    public static VariantResponse from(ProductVariant v) {
        return new VariantResponse(v.getId(), v.getSize(), v.getColor(), v.getSku(), v.getStock(),
                v.effectivePrice(), v.isActive());
    }
}
