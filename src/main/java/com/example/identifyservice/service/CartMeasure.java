package com.example.identifyservice.service;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;

/** Parcel weight in grams (min 1) and goods value in VND of a cart, computed from database data. */
public record CartMeasure(int weightGrams, long value) {
    public static CartMeasure of(Cart cart) {
        long weight = 0;
        long value = 0;
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = item.getVariant();
            weight += (long) item.getQuantity() * variant.getProduct().effectiveWeight();
            value += variant.effectivePrice() * item.getQuantity();
        }
        return new CartMeasure((int) Math.max(1, Math.min(weight, Integer.MAX_VALUE)), value);
    }
}
