package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.stats.StatsOverviewResponse;
import com.example.identifyservice.dto.response.stats.StatsSeriesPoint;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.testsupport.TestDataFactory;
import com.example.identifyservice.testsupport.TestDataFactory.Line;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class StatsServiceTest {
    static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    // Period used by most tests: 10 days; previous period is 2025-02-19 .. 2025-02-28.
    static final String FROM = "2025-03-01";
    static final String TO = "2025-03-10";

    @Autowired StatsService stats;
    @Autowired TestDataFactory data;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    /** Other test classes commit variants; hide them (rolled back with this test) so limits are deterministic. */
    private void hideForeignVariants(String ownSkuPrefix) {
        em.createQuery("update ProductVariant v set v.active = false where v.sku not like :p")
                .setParameter("p", ownSkuPrefix + "%").executeUpdate();
    }

    static Instant ict(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ICT).toInstant();
    }

    StatsOverviewResponse overview() {
        data.settle();
        return stats.overview(FROM, TO, "day");
    }

    /** A paid, non-cancelled MoMo order dated (created and paid) at the given Vietnam-local time. */
    void paid(String when, long shipping, Line... lines) {
        paid(PaymentMethod.MOMO, OrderStatus.CONFIRMED, when, shipping, lines);
    }

    void paid(PaymentMethod method, OrderStatus status, String when, long shipping, Line... lines) {
        data.datedOrder(data.user("buyer"), status, method, PaymentStatus.PAID, ict(when), ict(when), shipping, lines);
    }

    static Line line(String name, long price, int qty, Long cost) {
        return new Line(name, price, qty, cost);
    }

    // ---- revenue definition ----

    @Test
    void revenueCountsOnlyPaidNonCancelledOrdersDatedByPaidAt() {
        paid("2025-03-02T10:00:00", 30_000, line("Tee", 100_000, 2, null));
        paid(PaymentMethod.COD, OrderStatus.COMPLETED, "2025-03-03T10:00:00", 20_000, line("Tee", 50_000, 1, null));
        // excluded: paid then cancelled
        data.datedOrder(data.user("buyer"), OrderStatus.CANCELLED, PaymentMethod.MOMO, PaymentStatus.PAID,
                ict("2025-03-04T09:00:00"), ict("2025-03-04T09:05:00"), 10_000, line("Tee", 999_000, 1, null));
        // excluded: unpaid pending order
        data.datedOrder(data.user("buyer"), OrderStatus.PENDING_PAYMENT, PaymentMethod.MOMO, PaymentStatus.UNPAID,
                ict("2025-03-05T09:00:00"), null, 10_000, line("Tee", 777_000, 1, null));
        // excluded: COD pending confirm, not yet paid
        data.datedOrder(data.user("buyer"), OrderStatus.PENDING_CONFIRM, PaymentMethod.COD, PaymentStatus.UNPAID,
                ict("2025-03-05T09:00:00"), null, 10_000, line("Tee", 555_000, 1, null));

        var r = overview();

        assertThat(r.kpis().revenue().value()).isEqualTo(250_000);
        assertThat(r.kpis().paidOrders().value()).isEqualTo(2);
        assertThat(r.kpis().itemsSold().value()).isEqualTo(3);
    }

    @Test
    void shippingIsExcludedFromRevenueButReportedAsShippingCollected() {
        paid("2025-03-02T10:00:00", 30_000, line("Tee", 100_000, 1, null));
        paid("2025-03-03T10:00:00", 15_000, line("Tee", 100_000, 1, null));

        var r = overview();

        assertThat(r.kpis().revenue().value()).isEqualTo(200_000);
        assertThat(r.kpis().shippingCollected().value()).isEqualTo(45_000);
    }

    @Test
    void revenueIsDatedByPaidAtNotCreatedAt() {
        // created before the period, paid inside it: revenue yes, order of the period no
        data.datedOrder(data.user("buyer"), OrderStatus.CONFIRMED, PaymentMethod.MOMO, PaymentStatus.PAID,
                ict("2025-02-27T10:00:00"), ict("2025-03-01T08:00:00"), 0, line("Tee", 100_000, 1, null));
        // created inside the period, paid after it: order yes, revenue no
        data.datedOrder(data.user("buyer"), OrderStatus.CONFIRMED, PaymentMethod.MOMO, PaymentStatus.PAID,
                ict("2025-03-10T10:00:00"), ict("2025-03-12T08:00:00"), 0, line("Tee", 300_000, 1, null));

        var r = overview();

        assertThat(r.kpis().revenue().value()).isEqualTo(100_000);
        assertThat(r.kpis().paidOrders().value()).isEqualTo(1);
        assertThat(r.kpis().orders().value()).isEqualTo(1);
    }

    @Test
    void ordersAndCancelRateAreDatedByCreatedAtAnyStatus() {
        var u = data.user("buyer");
        data.datedOrder(u, OrderStatus.PENDING_PAYMENT, PaymentMethod.MOMO, PaymentStatus.UNPAID,
                ict("2025-03-02T10:00:00"), null, 0, line("Tee", 1_000, 1, null));
        data.datedOrder(u, OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.UNPAID,
                ict("2025-03-03T10:00:00"), null, 0, line("Tee", 1_000, 1, null));
        data.datedOrder(u, OrderStatus.CANCELLED, PaymentMethod.MOMO, PaymentStatus.EXPIRED,
                ict("2025-03-04T10:00:00"), null, 0, line("Tee", 1_000, 1, null));
        data.datedOrder(u, OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.PAID,
                ict("2025-03-05T10:00:00"), ict("2025-03-06T10:00:00"), 0, line("Tee", 1_000, 1, null));
        data.datedOrder(u, OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.UNPAID,
                ict("2025-03-20T10:00:00"), null, 0, line("Tee", 1_000, 1, null)); // outside the period

        var r = overview();

        assertThat(r.kpis().orders().value()).isEqualTo(4);
        assertThat(r.kpis().cancelledOrders().value()).isEqualTo(2);
        assertThat(r.kpis().cancelRate().value()).isEqualTo(0.5);
    }

    @Test
    void emptyPeriodGivesZerosAndNullProfit() {
        var r = overview();

        assertThat(r.kpis().revenue().value()).isZero();
        assertThat(r.kpis().averageOrderValue().value()).isZero();
        assertThat(r.kpis().cancelRate().value()).isZero();
        assertThat(r.kpis().profit().value()).isNull();
        assertThat(r.kpis().profit().previous()).isNull();
        assertThat(r.kpis().profit().coverage()).isZero();
        assertThat(r.topProducts()).isEmpty();
    }

    @Test
    void averageOrderValueIsRevenueOverPaidOrders() {
        paid("2025-03-02T10:00:00", 0, line("Tee", 100_000, 1, null));
        paid("2025-03-03T10:00:00", 0, line("Tee", 200_000, 1, null));
        paid("2025-03-04T10:00:00", 0, line("Tee", 300_000, 1, null));

        assertThat(overview().kpis().averageOrderValue().value()).isEqualTo(200_000);
    }

    // ---- boundaries ----

    @Test
    void periodBoundariesFollowVietnamMidnight() {
        paid("2025-03-10T23:59:59", 0, line("Last", 1_000, 1, null));     // included (last second of `to`)
        paid("2025-03-11T00:00:00", 0, line("Next", 5_000, 1, null));     // excluded
        paid("2025-03-01T00:00:00", 0, line("First", 10_000, 1, null));   // included (first second of `from`)
        paid("2025-02-28T23:59:59", 0, line("Prev", 100_000, 1, null));   // previous period only

        var r = overview();

        assertThat(r.kpis().revenue().value()).isEqualTo(11_000);
        assertThat(r.kpis().paidOrders().value()).isEqualTo(2);
        assertThat(r.kpis().revenue().previous()).isEqualTo(100_000);
    }

    @Test
    void instantOnPreviousUtcDateButSameVietnamDateLandsInVietnamBucket() {
        // 2025-03-05 03:00 ICT == 2025-03-04 20:00 UTC
        paid("2025-03-05T03:00:00", 0, line("Tee", 42_000, 1, null));

        var r = overview();

        assertThat(point(r, "2025-03-05").revenue()).isEqualTo(42_000);
        assertThat(point(r, "2025-03-04").revenue()).isZero();
    }

    // ---- previous period ----

    @Test
    void previousPeriodIsTheSameNumberOfDaysImmediatelyBefore() {
        var r = overview();

        assertThat(r.from()).isEqualTo(LocalDate.parse("2025-03-01"));
        assertThat(r.to()).isEqualTo(LocalDate.parse("2025-03-10"));
        assertThat(r.previousFrom()).isEqualTo(LocalDate.parse("2025-02-19"));
        assertThat(r.previousTo()).isEqualTo(LocalDate.parse("2025-02-28"));
        assertThat(r.groupBy()).isEqualTo("day");
    }

    @Test
    void everyKpiCarriesItsPreviousValue() {
        // previous period: 1 paid order 100k (2 items, cost known), 2 created orders (1 cancelled), 1 new customer
        paid("2025-02-20T10:00:00", 8_000, line("Tee", 50_000, 2, 30_000L));
        data.datedOrder(data.user("buyer"), OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.UNPAID,
                ict("2025-02-21T10:00:00"), null, 0, line("Tee", 1_000, 1, null));
        data.datedUser("prev-customer", ict("2025-02-22T10:00:00"), false);
        // current period
        paid("2025-03-02T10:00:00", 9_000, line("Tee", 80_000, 1, null));

        var k = overview().kpis();

        assertThat(k.revenue().previous()).isEqualTo(100_000);
        assertThat(k.paidOrders().previous()).isEqualTo(1);
        assertThat(k.averageOrderValue().previous()).isEqualTo(100_000);
        assertThat(k.orders().previous()).isEqualTo(2);
        assertThat(k.cancelledOrders().previous()).isEqualTo(1);
        assertThat(k.cancelRate().previous()).isEqualTo(0.5);
        assertThat(k.itemsSold().previous()).isEqualTo(2);
        assertThat(k.shippingCollected().previous()).isEqualTo(8_000);
        assertThat(k.newCustomers().previous()).isEqualTo(1);
        assertThat(k.profit().previous()).isEqualTo(40_000);
        assertThat(k.profit().value()).isNull();
        assertThat(k.revenue().value()).isEqualTo(80_000);
    }

    // ---- series ----

    @Test
    void daySeriesIsZeroFilledForEveryDay() {
        paid("2025-03-03T10:00:00", 0, line("Tee", 100_000, 1, 60_000L));
        data.datedOrder(data.user("buyer"), OrderStatus.PENDING_PAYMENT, PaymentMethod.MOMO, PaymentStatus.UNPAID,
                ict("2025-03-03T11:00:00"), null, 0, line("Tee", 1, 1, null));

        var r = overview();

        assertThat(r.series()).hasSize(10);
        assertThat(r.series().get(0).bucket()).isEqualTo("2025-03-01");
        assertThat(r.series().get(9).bucket()).isEqualTo("2025-03-10");
        var day = point(r, "2025-03-03");
        assertThat(day.revenue()).isEqualTo(100_000);
        assertThat(day.orders()).isEqualTo(2);
        assertThat(day.paidOrders()).isEqualTo(1);
        assertThat(day.profit()).isEqualTo(40_000);
        var empty = point(r, "2025-03-04");
        assertThat(empty.revenue()).isZero();
        assertThat(empty.orders()).isZero();
        assertThat(empty.paidOrders()).isZero();
        assertThat(empty.profit()).isNull();
    }

    @Test
    void monthGroupingBucketsByYearMonthWithPartialEdges() {
        paid("2025-01-20T10:00:00", 0, line("Tee", 10_000, 1, null));
        paid("2025-02-15T10:00:00", 0, line("Tee", 20_000, 1, null));
        paid("2025-04-30T23:30:00", 0, line("Tee", 40_000, 1, null));
        paid("2025-05-01T00:30:00", 0, line("Tee", 80_000, 1, null)); // outside
        data.settle();

        var r = stats.overview("2025-01-15", "2025-04-30", "MONTH"); // case-insensitive

        assertThat(r.groupBy()).isEqualTo("month");
        assertThat(r.series()).extracting(StatsSeriesPoint::bucket)
                .containsExactly("2025-01", "2025-02", "2025-03", "2025-04");
        assertThat(r.series()).extracting(StatsSeriesPoint::revenue)
                .containsExactly(10_000L, 20_000L, 0L, 40_000L);
    }

    @Test
    void yearGroupingBucketsByYear() {
        paid("2022-12-31T23:00:00", 0, line("Tee", 10_000, 1, null));
        paid("2023-06-01T10:00:00", 0, line("Tee", 20_000, 1, null));
        paid("2024-02-01T10:00:00", 0, line("Tee", 30_000, 1, null));
        data.settle();

        var r = stats.overview("2022-01-01", "2024-12-31", "year");

        assertThat(r.groupBy()).isEqualTo("year");
        assertThat(r.series()).extracting(StatsSeriesPoint::bucket).containsExactly("2022", "2023", "2024");
        assertThat(r.series()).extracting(StatsSeriesPoint::revenue).containsExactly(10_000L, 20_000L, 30_000L);
    }

    // ---- customers ----

    @Test
    void newCustomersExcludeAdminsAndOutOfRangeUsers() {
        data.datedUser("c-in-1", ict("2025-03-01T00:00:00"), false);
        data.datedUser("c-in-2", ict("2025-03-10T23:59:59"), false);
        data.datedUser("admin-in", ict("2025-03-05T10:00:00"), true);
        data.datedUser("c-after", ict("2025-03-11T00:00:00"), false);
        data.datedUser("c-before", ict("2025-02-28T23:59:59"), false);

        var r = overview();

        assertThat(r.kpis().newCustomers().value()).isEqualTo(2);
        assertThat(r.kpis().newCustomers().previous()).isEqualTo(1);
    }

    // ---- profit ----

    @Test
    void profitAndCoverageUseOnlyItemsWithKnownCost() {
        // known: (100k-60k)*2 = 80k on value 200k; unknown: value 100k
        paid("2025-03-02T10:00:00", 0, line("A", 100_000, 2, 60_000L), line("B", 100_000, 1, null));

        var p = overview().kpis().profit();

        assertThat(p.value()).isEqualTo(80_000);
        assertThat(p.coverage()).isEqualTo(200_000.0 / 300_000.0);
    }

    @Test
    void profitIsNullAndCoverageZeroWhenNoCostIsKnown() {
        paid("2025-03-02T10:00:00", 0, line("A", 100_000, 2, null));

        var p = overview().kpis().profit();

        assertThat(p.value()).isNull();
        assertThat(p.coverage()).isZero();
    }

    @Test
    void zeroCostCountsAsKnownAndNegativeMarginIsAllowed() {
        paid("2025-03-02T10:00:00", 0, line("Gift", 10_000, 1, 0L), line("Loss", 10_000, 1, 15_000L));

        var p = overview().kpis().profit();

        assertThat(p.value()).isEqualTo(10_000 - 5_000);
        assertThat(p.coverage()).isEqualTo(1.0);
    }

    // ---- breakdowns ----

    @Test
    void statusBreakdownListsEveryStatusZeroFilled() {
        var u = data.user("buyer");
        data.datedOrder(u, OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.PAID,
                ict("2025-03-02T10:00:00"), ict("2025-03-03T10:00:00"), 0, line("Tee", 1_000, 1, null));
        data.datedOrder(u, OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.PAID,
                ict("2025-03-02T11:00:00"), ict("2025-03-03T11:00:00"), 0, line("Tee", 1_000, 1, null));
        data.datedOrder(u, OrderStatus.SHIPPING, PaymentMethod.MOMO, PaymentStatus.PAID,
                ict("2025-03-02T12:00:00"), ict("2025-03-02T12:30:00"), 0, line("Tee", 1_000, 1, null));

        var b = overview().statusBreakdown();

        assertThat(b).extracting(s -> s.status()).containsExactly(OrderStatus.values());
        assertThat(b.stream().filter(s -> s.status() == OrderStatus.COMPLETED).findFirst().get().count()).isEqualTo(2);
        assertThat(b.stream().filter(s -> s.status() == OrderStatus.SHIPPING).findFirst().get().count()).isEqualTo(1);
        assertThat(b.stream().filter(s -> s.status() == OrderStatus.CANCELLED).findFirst().get().count()).isZero();
    }

    @Test
    void paymentBreakdownCountsCreatedOrdersAndRevenuePerMethod() {
        var u = data.user("buyer");
        paid(PaymentMethod.MOMO, OrderStatus.CONFIRMED, "2025-03-02T10:00:00", 0, line("Tee", 100_000, 1, null));
        paid(PaymentMethod.COD, OrderStatus.COMPLETED, "2025-03-03T10:00:00", 0, line("Tee", 30_000, 1, null));
        data.datedOrder(u, OrderStatus.CANCELLED, PaymentMethod.MOMO, PaymentStatus.EXPIRED,
                ict("2025-03-04T10:00:00"), null, 0, line("Tee", 500_000, 1, null));

        var b = overview().paymentBreakdown();

        assertThat(b).extracting(s -> s.method()).containsExactly(PaymentMethod.values());
        var momo = b.stream().filter(s -> s.method() == PaymentMethod.MOMO).findFirst().get();
        var cod = b.stream().filter(s -> s.method() == PaymentMethod.COD).findFirst().get();
        assertThat(momo.orders()).isEqualTo(2);
        assertThat(momo.revenue()).isEqualTo(100_000);
        assertThat(cod.orders()).isEqualTo(1);
        assertThat(cod.revenue()).isEqualTo(30_000);
    }

    // ---- top products ----

    @Test
    void topProductsAreRankedByRevenueThenQuantityThenName() {
        paid("2025-03-02T10:00:00", 0,
                line("Zeta", 10_000, 1, null),      // revenue 10k, qty 1
                line("Alpha", 5_000, 2, null),      // revenue 10k, qty 2
                line("Beta", 2_500, 4, null),       // revenue 10k, qty 4: before Alpha
                line("Gamma", 5_000, 2, null),      // ties Alpha on revenue and quantity: name order
                line("Big", 90_000, 1, 50_000L));
        paid("2025-03-03T10:00:00", 0, line("Big", 90_000, 1, 70_000L)); // merged by name: 180k, profit 60k

        var top = overview().topProducts();

        assertThat(top).extracting(t -> t.productName()).containsExactly("Big", "Beta", "Alpha", "Gamma", "Zeta");
        assertThat(top.get(0).revenue()).isEqualTo(180_000);
        assertThat(top.get(0).quantity()).isEqualTo(2);
        assertThat(top.get(0).profit()).isEqualTo(60_000);
        assertThat(top.get(1).profit()).isNull();
    }

    @Test
    void topProductsAreLimitedToTen() {
        Line[] lines = new Line[12];
        for (int i = 0; i < 12; i++) lines[i] = line("P" + String.format("%02d", i), 1_000L * (i + 1), 1, null);
        paid("2025-03-02T10:00:00", 0, lines);

        var top = overview().topProducts();

        assertThat(top).hasSize(10);
        assertThat(top.get(0).productName()).isEqualTo("P11");
        assertThat(top.get(9).productName()).isEqualTo("P02");
    }

    // ---- low stock ----

    @Test
    void lowStockKeepsActiveVariantsOfActiveProductsAtOrBelowFiveLowestFirst() {
        var active = data.product("ls-active", 100_000, true);
        data.variant(active, "M", "red", 5, null);
        data.variant(active, "L", "red", 2, null);
        data.variant(active, "S", "red", 6, null);                    // > 5: excluded
        var inactiveVariant = data.variant(active, "XL", "red", 0, null);
        inactiveVariant.setActive(false);
        var inactiveProduct = data.product("ls-inactive", 100_000, false);
        data.variant(inactiveProduct, "M", "red", 0, null);           // inactive product: excluded
        var other = data.product("ls-other", 100_000, true);
        data.variant(other, "M", "blue", 2, null);                    // ties stock 2: product name order
        hideForeignVariants("ls-");

        var low = overview().lowStock().stream().filter(l -> l.sku().startsWith("ls-")).toList();

        assertThat(low).extracting(l -> l.sku())
                .containsExactly("ls-active-L-red", "ls-other-M-blue", "ls-active-M-red");
        assertThat(low.get(0).productName()).isEqualTo("Product ls-active");
        assertThat(low.get(0).size()).isEqualTo("L");
        assertThat(low.get(0).color()).isEqualTo("red");
        assertThat(low.get(0).stock()).isEqualTo(2);
    }

    @Test
    void lowStockIsLimitedToTen() {
        var p = data.product("ls-many", 100_000, true);
        for (int i = 0; i < 12; i++) data.variant(p, "S" + i, "red", i % 6, null);
        hideForeignVariants("ls-");

        var low = overview().lowStock();

        assertThat(low).hasSize(10);
        assertThat(low).extracting(l -> l.stock()).isSorted();
    }

    // ---- validation / defaults / security ----

    private void assertInvalid(String from, String to, String groupBy) {
        assertThatThrownBy(() -> stats.overview(from, to, groupBy))
                .isInstanceOf(AppException.class)
                .satisfies(t -> assertThat(((AppException) t).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void invalidInputsAreRejectedWithInvalidInput() {
        assertInvalid("2025-03-10", "2025-03-01", "day");   // from > to
        assertInvalid("2020-01-01", "2023-12-31", "month"); // 1461 days > 1100
        assertInvalid("2024-01-01", "2025-01-01", "day");   // 367 days
        assertInvalid("2025-13-45", "2025-03-01", "day");   // bad date
        assertInvalid("2025-03-01", "nope", "day");         // bad date
        assertInvalid("2025-03-01", "2025-03-02", "week");  // bad groupBy
    }

    @Test
    void limitsAreInclusiveAt366DaysForDayAnd1100DaysOverall() {
        assertThat(stats.overview("2024-01-01", "2024-12-31", "day").series()).hasSize(366);
        assertThat(stats.overview("2023-01-01", "2026-01-04", "month").series()).isNotEmpty(); // exactly 1100 days
        assertInvalid("2023-01-01", "2026-01-05", "month");                                      // 1101 days
    }

    @Test
    void defaultsAreLast30DaysEndingTodayInVietnamGroupedByDay() {
        var r = stats.overview(null, null, null);
        LocalDate today = LocalDate.now(ICT);

        assertThat(r.to()).isEqualTo(today);
        assertThat(r.from()).isEqualTo(today.minusDays(29));
        assertThat(r.groupBy()).isEqualTo("day");
        assertThat(r.series()).hasSize(30);
        assertThat(r.previousTo()).isEqualTo(today.minusDays(30));
        assertThat(r.previousFrom()).isEqualTo(today.minusDays(59));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminIsDenied() {
        assertThatThrownBy(() -> stats.overview(FROM, TO, "day")).isInstanceOf(AccessDeniedException.class);
    }

    private static StatsSeriesPoint point(StatsOverviewResponse r, String bucket) {
        return r.series().stream().filter(p -> p.bucket().equals(bucket)).findFirst().orElseThrow();
    }
}
