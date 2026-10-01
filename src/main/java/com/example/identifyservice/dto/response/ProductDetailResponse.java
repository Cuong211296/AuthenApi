package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Product;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ProductDetailResponse(String id, String name, String slug, String description, String imageUrl,
                                    long basePrice, boolean active, CategoryResponse category,
                                    List<VariantResponse> variants,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) Long costPrice,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) Integer weightGrams) {
    /** publicView hides inactive variants. */
    public static ProductDetailResponse from(Product p, boolean publicView) {
        return new ProductDetailResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getImageUrl(),
                p.getBasePrice(), p.isActive(), CategoryResponse.from(p.getCategory()),
                p.getVariants().stream()
                        .filter(v -> !publicView || v.isActive())
                        .map(VariantResponse::from)
                        .toList(),
                publicView ? null : p.getCostPrice(),
                publicView ? null : p.getWeightGrams());
    }
}
