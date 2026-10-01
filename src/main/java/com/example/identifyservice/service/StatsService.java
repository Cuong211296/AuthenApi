package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.stats.StatsKpis;
import com.example.identifyservice.dto.response.stats.StatsLowStock;
import com.example.identifyservice.dto.response.stats.StatsMetric;
import com.example.identifyservice.dto.response.stats.StatsOverviewResponse;
import com.example.identifyservice.dto.response.stats.StatsPaymentSplit;
import com.example.identifyservice.dto.response.stats.StatsProfitMetric;
import com.example.identifyservice.dto.response.stats.StatsRateMetric;
import com.example.identifyservice.dto.response.stats.StatsSeriesPoint;
import com.example.identifyservice.dto.response.stats.StatsStatusCount;
import com.example.identifyservice.dto.response.stats.StatsTopProduct;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.OrderItem;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.OrderRepository.CreatedOrderRow;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin statistics. Rows for the window (current + previous period) are fetched once and aggregated in Java,
 * bucketed by calendar date in Vietnam time, so it behaves the same on MySQL and H2.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class StatsService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_RANGE_DAYS = 1100;
    static final int MAX_DAY_GROUPING_DAYS = 366;
    static final int DEFAULT_DAYS = 30;
    static final int LOW_STOCK_THRESHOLD = 5;
    static final int LIST_LIMIT = 10;

    OrderRepository orderRepository;
    UserRepository userRepository;
    ProductVariantRepository variantRepository;

    private enum Grouping { DAY, MONTH, YEAR }

    /** Gross profit accumulator over items with a known unit cost. */
    private static final class ProfitAcc {
        long profit;
        long knownValue;
        long totalValue;
        boolean any;

        void add(OrderItem item) {
            long value = item.getUnitPrice() * item.getQuantity();
            totalValue += value;
            if (item.getUnitCost() != null) {
                any = true;
                knownValue += value;
                profit += (item.getUnitPrice() - item.getUnitCost()) * item.getQuantity();
            }
        }

        Long profit() {
            return any ? profit : null;
        }

        double coverage() {
            return totalValue == 0 ? 0.0 : (double) knownValue / totalValue;
        }
    }

    /** KPI values of one period. */
    private record PeriodTotals(long revenue, long orders, long paidOrders, long averageOrderValue, long newCustomers,
                                long cancelledOrders, double cancelRate, long itemsSold, long shippingCollected,
                                Long profit, double coverage) {}

    @Transactional(readOnly = true)
    public StatsOverviewResponse overview(String fromParam, String toParam, String groupByParam) {
        LocalDate to = parseDate(toParam);
        LocalDate from = parseDate(fromParam);
        if (to == null) to = LocalDate.now(ZONE);
        if (from == null) from = to.minusDays(DEFAULT_DAYS - 1L);
        Grouping grouping = parseGrouping(groupByParam);

        if (from.isAfter(to)) throw new AppException(ErrorCode.INVALID_INPUT);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) throw new AppException(ErrorCode.INVALID_INPUT);
        if (grouping == Grouping.DAY && days > MAX_DAY_GROUPING_DAYS) throw new AppException(ErrorCode.INVALID_INPUT);

        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = from.minusDays(days);
        Instant previousStart = startOf(previousFrom);
        Instant currentStart = startOf(from);
        Instant end = startOf(to.plusDays(1));

        List<Order> revenueOrders = orderRepository.findRevenueOrdersWithItems(previousStart, end);
        List<CreatedOrderRow> createdRows = orderRepository.findCreatedOrderRows(previousStart, end);
        List<Instant> customers = userRepository.findCustomerCreatedAtBetween(previousStart, end);

        List<Order> currentRevenue = revenueOrders.stream().filter(o -> !o.getPaidAt().isBefore(currentStart)).toList();
        List<Order> previousRevenue = revenueOrders.stream().filter(o -> o.getPaidAt().isBefore(currentStart)).toList();
        List<CreatedOrderRow> currentCreated = createdRows.stream()
                .filter(r -> !r.getCreatedAt().isBefore(currentStart)).toList();
        List<CreatedOrderRow> previousCreated = createdRows.stream()
                .filter(r -> r.getCreatedAt().isBefore(currentStart)).toList();
        long currentCustomers = customers.stream().filter(i -> !i.isBefore(currentStart)).count();
        long previousCustomers = customers.size() - currentCustomers;

        PeriodTotals cur = totals(currentRevenue, currentCreated, currentCustomers);
        PeriodTotals prev = totals(previousRevenue, previousCreated, previousCustomers);

        StatsKpis kpis = new StatsKpis(
                new StatsMetric(cur.revenue(), prev.revenue()),
                new StatsMetric(cur.orders(), prev.orders()),
                new StatsMetric(cur.paidOrders(), prev.paidOrders()),
                new StatsMetric(cur.averageOrderValue(), prev.averageOrderValue()),
                new StatsMetric(cur.newCustomers(), prev.newCustomers()),
                new StatsMetric(cur.cancelledOrders(), prev.cancelledOrders()),
                new StatsRateMetric(cur.cancelRate(), prev.cancelRate()),
                new StatsMetric(cur.itemsSold(), prev.itemsSold()),
                new StatsMetric(cur.shippingCollected(), prev.shippingCollected()),
                new StatsProfitMetric(cur.profit(), prev.profit(), cur.coverage()));

        return new StatsOverviewResponse(from, to, grouping.name().toLowerCase(Locale.ROOT), previousFrom, previousTo,
                kpis,
                series(from, to, grouping, currentRevenue, currentCreated),
                statusBreakdown(currentCreated),
                paymentBreakdown(currentRevenue, currentCreated),
                topProducts(currentRevenue),
                lowStock());
    }

    // ---- parsing ----

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
    }

    private static Grouping parseGrouping(String value) {
        if (value == null || value.isBlank()) return Grouping.DAY;
        try {
            return Grouping.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    private static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    // ---- aggregation ----

    private static PeriodTotals totals(List<Order> revenueOrders, List<CreatedOrderRow> created, long newCustomers) {
        long revenue = 0, shipping = 0, items = 0;
        ProfitAcc profit = new ProfitAcc();
        for (Order o : revenueOrders) {
            revenue += o.getSubtotal();
            shipping += o.getShippingFee();
            for (OrderItem item : o.getItems()) {
                items += item.getQuantity();
                profit.add(item);
            }
        }
        long paid = revenueOrders.size();
        long cancelled = created.stream().filter(r -> r.getStatus() == OrderStatus.CANCELLED).count();
        long orders = created.size();
        return new PeriodTotals(revenue, orders, paid, paid == 0 ? 0 : Math.round((double) revenue / paid),
                newCustomers, cancelled, orders == 0 ? 0.0 : (double) cancelled / orders, items, shipping,
                profit.profit(), profit.coverage());
    }

    private static String bucketOf(LocalDate date, Grouping grouping) {
        return switch (grouping) {
            case DAY -> date.toString();
            case MONTH -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case YEAR -> String.format("%04d", date.getYear());
        };
    }

    private static List<StatsSeriesPoint> series(LocalDate from, LocalDate to, Grouping grouping,
                                                 List<Order> revenueOrders, List<CreatedOrderRow> created) {
        Map<String, long[]> counts = new LinkedHashMap<>(); // revenue, orders, paidOrders
        Map<String, ProfitAcc> profits = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = bucketOf(d, grouping);
            counts.computeIfAbsent(key, k -> new long[3]);
            profits.computeIfAbsent(key, k -> new ProfitAcc());
        }
        for (Order o : revenueOrders) {
            String key = bucketOf(dateOf(o.getPaidAt()), grouping);
            long[] c = counts.get(key);
            c[0] += o.getSubtotal();
            c[2]++;
            for (OrderItem item : o.getItems()) profits.get(key).add(item);
        }
        for (CreatedOrderRow r : created) counts.get(bucketOf(dateOf(r.getCreatedAt()), grouping))[1]++;

        List<StatsSeriesPoint> series = new ArrayList<>();
        counts.forEach((key, c) -> series.add(new StatsSeriesPoint(key, c[0], c[1], c[2], profits.get(key).profit())));
        return series;
    }

    private static List<StatsStatusCount> statusBreakdown(List<CreatedOrderRow> created) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (CreatedOrderRow r : created) counts.merge(r.getStatus(), 1L, Long::sum);
        List<StatsStatusCount> result = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            result.add(new StatsStatusCount(status, counts.getOrDefault(status, 0L)));
        }
        return result;
    }

    private static List<StatsPaymentSplit> paymentBreakdown(List<Order> revenueOrders, List<CreatedOrderRow> created) {
        Map<PaymentMethod, Long> orders = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, Long> revenue = new EnumMap<>(PaymentMethod.class);
        for (CreatedOrderRow r : created) orders.merge(r.getPaymentMethod(), 1L, Long::sum);
        for (Order o : revenueOrders) revenue.merge(o.getPaymentMethod(), o.getSubtotal(), Long::sum);
        List<StatsPaymentSplit> result = new ArrayList<>();
        for (PaymentMethod m : PaymentMethod.values()) {
            result.add(new StatsPaymentSplit(m, orders.getOrDefault(m, 0L), revenue.getOrDefault(m, 0L)));
        }
        return result;
    }

    private static final class ProductAcc {
        long quantity;
        long revenue;
        final ProfitAcc profit = new ProfitAcc();
    }

    private static List<StatsTopProduct> topProducts(List<Order> revenueOrders) {
        Map<String, ProductAcc> byName = new HashMap<>();
        for (Order o : revenueOrders) {
            for (OrderItem item : o.getItems()) {
                ProductAcc acc = byName.computeIfAbsent(item.getProductName(), k -> new ProductAcc());
                acc.quantity += item.getQuantity();
                acc.revenue += item.getUnitPrice() * item.getQuantity();
                acc.profit.add(item);
            }
        }
        return byName.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, ProductAcc>>comparingLong(e -> -e.getValue().revenue)
                        .thenComparingLong(e -> -e.getValue().quantity)
                        .thenComparing(Map.Entry::getKey))
                .limit(LIST_LIMIT)
                .map(e -> new StatsTopProduct(e.getKey(), e.getValue().quantity, e.getValue().revenue,
                        e.getValue().profit.profit()))
                .toList();
    }

    private List<StatsLowStock> lowStock() {
        return variantRepository.findLowStock(LOW_STOCK_THRESHOLD, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(v -> new StatsLowStock(v.getProduct().getName(), v.getSize(), v.getColor(), v.getSku(), v.getStock()))
                .toList();
    }
}
