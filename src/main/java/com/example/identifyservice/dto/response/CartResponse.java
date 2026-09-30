package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Cart;

import java.util.List;

public record CartResponse(List<CartItemResponse> items, long subtotal, int totalQuantity) {
    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(CartItemResponse::from).toList();
        return new CartResponse(items, items.stream().mapToLong(CartItemResponse::lineTotal).sum(),
                items.stream().mapToInt(CartItemResponse::quantity).sum());
    }
}
