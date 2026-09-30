package com.example.identifyservice.dto.response;

import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;

public record PaymentResultResponse(String orderCode, OrderStatus orderStatus, PaymentStatus paymentStatus) {
}
