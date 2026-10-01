package com.example.identifyservice.dto.response.stats;

import com.example.identifyservice.enums.PaymentMethod;

public record StatsPaymentSplit(PaymentMethod method, long orders, long revenue) {}
