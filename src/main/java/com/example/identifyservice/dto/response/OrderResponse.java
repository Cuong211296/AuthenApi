package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.enums.ShippingSource;

import java.time.Instant;
import java.util.List;

public record OrderResponse(String code, OrderStatus status, PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                            long subtotal, long shippingFee, long total, String receiverName, String phone,
                            String email, String address, String province, String district, String ward, String note, Instant createdAt,
                            Instant paidAt, Instant expiresAt, ShippingSource shippingSource,
                            Integer weightGrams, List<OrderItemResponse> items) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getCode(), o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(),
                o.getSubtotal(), o.getShippingFee(), o.getTotal(), o.getReceiverName(), o.getPhone(), o.getEmail(),
                o.getAddress(), o.getProvince(), o.getDistrict(), o.getWard(), o.getNote(), o.getCreatedAt(), o.getPaidAt(), o.getExpiresAt(),
                o.getShippingSource(), o.getWeightGrams(),
                o.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
