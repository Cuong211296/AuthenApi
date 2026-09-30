package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Product;

public record ProductSummaryResponse(String id, String name, String slug, String imageUrl, long basePrice,
                                     String categoryName, boolean active) {
    public static ProductSummaryResponse from(Product p) {
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getImageUrl(), p.getBasePrice(),
                p.getCategory() == null ? null : p.getCategory().getName(), p.isActive());
    }
}
