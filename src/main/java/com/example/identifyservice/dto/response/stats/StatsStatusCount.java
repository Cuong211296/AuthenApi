package com.example.identifyservice.dto.response.stats;

import com.example.identifyservice.enums.OrderStatus;

public record StatsStatusCount(OrderStatus status, long count) {}
