package com.example.identifyservice.dto.response.stats;

public record StatsKpis(
        StatsMetric revenue,
        StatsMetric orders,
        StatsMetric paidOrders,
        StatsMetric averageOrderValue,
        StatsMetric newCustomers,
        StatsMetric cancelledOrders,
        StatsRateMetric cancelRate,
        StatsMetric itemsSold,
        StatsMetric shippingCollected,
        StatsProfitMetric profit) {}
