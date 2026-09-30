package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.OrderItem;

public record OrderItemResponse(String productName, String size, String color, long unitPrice, int quantity,
                                long lineTotal) {
    public static OrderItemResponse from(OrderItem i) {
        return new OrderItemResponse(i.getProductName(), i.getSize(), i.getColor(), i.getUnitPrice(),
                i.getQuantity(), i.getUnitPrice() * i.getQuantity());
    }
}
