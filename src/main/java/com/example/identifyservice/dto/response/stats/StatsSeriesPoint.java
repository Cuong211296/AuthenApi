package com.example.identifyservice.dto.response.stats;

public record StatsSeriesPoint(String bucket, long revenue, long orders, long paidOrders, Long profit) {}
