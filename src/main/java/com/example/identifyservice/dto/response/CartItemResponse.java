package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;

public record CartItemResponse(String variantId, String productSlug, String productName, String imageUrl,
                               String size, String color, long unitPrice, int quantity, long lineTotal,
                               int stock, boolean available) {
    public static CartItemResponse from(CartItem item) {
        ProductVariant v = item.getVariant();
        Product p = v.getProduct();
        long unit = v.effectivePrice();
        return new CartItemResponse(v.getId(), p.getSlug(), p.getName(), p.getImageUrl(), v.getSize(), v.getColor(),
                unit, item.getQuantity(), unit * item.getQuantity(), v.getStock(),
                v.isActive() && p.isActive() && v.getStock() >= item.getQuantity());
    }
}
