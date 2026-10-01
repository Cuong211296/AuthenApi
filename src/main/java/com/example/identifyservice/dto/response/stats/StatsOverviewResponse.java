package com.example.identifyservice.dto.response.stats;

import java.time.LocalDate;
import java.util.List;

public record StatsOverviewResponse(
        LocalDate from,
        LocalDate to,
        String groupBy,
        LocalDate previousFrom,
        LocalDate previousTo,
        StatsKpis kpis,
        List<StatsSeriesPoint> series,
        List<StatsStatusCount> statusBreakdown,
        List<StatsPaymentSplit> paymentBreakdown,
        List<StatsTopProduct> topProducts,
        List<StatsLowStock> lowStock) {}
