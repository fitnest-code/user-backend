package az.fitnest.user.service.dashboard;

import az.fitnest.user.client.dashboard.DashboardSiblingClient;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardCustomersBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardFinanceBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardGymRow;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardPackageCardBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardQrBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardSubscriptionsBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardTopGymBlock;
import az.fitnest.user.dto.response.UserStatisticsResponse;
import az.fitnest.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardV2Service {

    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    private final DashboardSiblingClient siblingClient;
    private final AdminUserService adminUserService;

    @Cacheable(cacheNames = "dashboard-v2-summary", key = "{#dateFrom, #dateTo}", sync = true)
    public DashboardSummaryResponse getSummary(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate[] range = resolveRange(dateFrom, dateTo);
        LocalDate from = range[0];
        LocalDate to = range[1];
        List<String> blockErrors = new ArrayList<>();

        Map<String, Object> incomeRaw = Map.of();
        Map<String, Object> obligationsRaw = Map.of();
        Map<String, Object> subscriptionsRaw = Map.of();
        Map<String, Object> topGymsRaw = Map.of();
        Map<String, Object> qrScansRaw = Map.of();
        Map<String, Object> appQrRaw = Map.of();
        List<Map<String, Object>> homeGymRaw = List.of();

        try {
            incomeRaw = siblingClient.fetchIncomeSummary(from, to);
        } catch (Exception e) {
            log.warn("dashboard income failed: {}", e.getMessage());
            blockErrors.add("income");
        }
        try {
            obligationsRaw = siblingClient.fetchGymObligations(from, to);
        } catch (Exception e) {
            log.warn("dashboard obligations failed: {}", e.getMessage());
            blockErrors.add("obligations");
        }
        try {
            subscriptionsRaw = siblingClient.fetchSubscriptionSummary(from, to);
        } catch (Exception e) {
            log.warn("dashboard subscriptions failed: {}", e.getMessage());
            blockErrors.add("subscriptions");
        }
        try {
            topGymsRaw = siblingClient.fetchTopGyms(from, to, 10);
        } catch (Exception e) {
            log.warn("dashboard top-gyms failed: {}", e.getMessage());
            blockErrors.add("topGyms");
        }
        try {
            qrScansRaw = siblingClient.fetchQrScans(from, to);
        } catch (Exception e) {
            log.warn("dashboard qr-scans failed: {}", e.getMessage());
            blockErrors.add("qrScans");
        }
        try {
            appQrRaw = siblingClient.fetchAppQrReport();
        } catch (Exception e) {
            log.warn("dashboard app-qr failed: {}", e.getMessage());
            blockErrors.add("appQr");
        }
        try {
            homeGymRaw = siblingClient.fetchHomeGymCounts();
        } catch (Exception e) {
            log.warn("dashboard home-gym-counts failed: {}", e.getMessage());
            blockErrors.add("homeGym");
        }

        DashboardFinanceBlock finance = buildFinance(incomeRaw, obligationsRaw);
        DashboardCustomersBlock customers = buildCustomers(blockErrors);
        DashboardSubscriptionsBlock subscriptions = mapSubscriptions(subscriptionsRaw);
        List<DashboardPackageCardBlock> packageCards = mapPackageCards(subscriptionsRaw);
        DashboardQrBlock qr = buildQr(qrScansRaw, appQrRaw);
        List<DashboardTopGymBlock> topGyms = mapTopGyms(topGymsRaw, homeGymRaw);

        return new DashboardSummaryResponse(
                finance,
                customers,
                subscriptions,
                packageCards,
                qr,
                topGyms,
                List.copyOf(blockErrors),
                from.toString(),
                to.toString()
        );
    }

    private DashboardFinanceBlock buildFinance(Map<String, Object> income, Map<String, Object> obligations) {
        double totalIncome = asDouble(income.get("totalIncome"));
        Double incomeDelta = asDoubleObj(income.get("incomeDeltaPct"));
        boolean incomePositive = asBoolean(income.get("incomePositiveTrend"));
        double refund = asDouble(income.get("refundAmount"));
        double bnpl = asDouble(income.get("pendingBnplAmount"));

        double totalObligation = asDouble(obligations.get("totalObligation"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gyms = (List<Map<String, Object>>) obligations.getOrDefault("gyms", List.of());
        List<DashboardGymRow> rows = gyms.stream()
                .map(g -> new DashboardGymRow(
                        asLong(g.get("gymId")),
                        asString(g.get("gymName")),
                        asLong(g.get("qrCount")),
                        asDouble(g.get("amount"))))
                .toList();

        double net = totalIncome - totalObligation;
        double margin = totalIncome > 0 ? (net * 100.0) / totalIncome : 0.0;
        // Obligation / net period deltas are not yet tracked separately — reuse income trend for net.
        return new DashboardFinanceBlock(
                totalIncome,
                incomeDelta,
                incomePositive,
                totalObligation,
                incomeDelta,
                incomePositive,
                net,
                incomeDelta,
                incomePositive,
                Math.round(margin * 10.0) / 10.0,
                refund,
                bnpl,
                rows
        );
    }

    private DashboardCustomersBlock buildCustomers(List<String> blockErrors) {
        try {
            UserStatisticsResponse stats = adminUserService.getUserStatistics();
            long total = stats.totalUsers();
            long active = stats.activeOrFrozenSubscriptions();
            long last7 = stats.usersWithLast7Days();
            double conversion = total > 0 ? (active * 100.0) / total : 0.0;
            return new DashboardCustomersBlock(
                    total, null,
                    active, null,
                    last7, null,
                    Math.round(conversion * 10.0) / 10.0, null
            );
        } catch (Exception e) {
            log.warn("dashboard customers stats failed: {}", e.getMessage());
            blockErrors.add("customers");
            return new DashboardCustomersBlock(0, null, 0, null, 0, null, 0, null);
        }
    }

    @SuppressWarnings("unchecked")
    private static DashboardSubscriptionsBlock mapSubscriptions(Map<String, Object> raw) {
        return new DashboardSubscriptionsBlock(
                asLong(raw.get("last7DaysCount")),
                asLong(raw.get("activeCount")),
                asLong(raw.get("frozenCount")),
                asLong(raw.get("endedInPeriod")),
                asLong(raw.get("renewedInPeriod")),
                asDoubleObj(raw.get("activeDeltaPct")),
                asBoolean(raw.get("activePositiveTrend"))
        );
    }

    @SuppressWarnings("unchecked")
    private static List<DashboardPackageCardBlock> mapPackageCards(Map<String, Object> raw) {
        List<Map<String, Object>> cards = (List<Map<String, Object>>) raw.getOrDefault("packageCards", List.of());
        return cards.stream()
                .map(c -> new DashboardPackageCardBlock(
                        asLong(c.get("packageId")),
                        asString(c.get("packageName")),
                        asLong(c.get("activeCount")),
                        asLong(c.get("frozenCount")),
                        asLong(c.get("last7DaysCount")),
                        asLong(c.get("endingIn30DaysCount")),
                        asDouble(c.get("sharePct")),
                        null
                ))
                .toList();
    }

    private static DashboardQrBlock buildQr(Map<String, Object> entrance, Map<String, Object> appQr) {
        long total = asLong(entrance.get("count"));
        Double totalDelta = asDoubleObj(entrance.get("percentageChange"));
        long light = asLong(appQr.get("lightCount"));
        long dark = asLong(appQr.get("darkCount"));
        // App QR report has no period delta today.
        return new DashboardQrBlock(total, totalDelta, light, null, dark, null);
    }

    @SuppressWarnings("unchecked")
    private static List<DashboardTopGymBlock> mapTopGyms(
            Map<String, Object> raw, List<Map<String, Object>> homeGymRaw) {
        Map<Long, Long> homeCounts = new HashMap<>();
        for (Map<String, Object> row : homeGymRaw) {
            homeCounts.put(asLong(row.get("gymId")), asLong(row.get("count")));
        }
        List<Map<String, Object>> gyms = (List<Map<String, Object>>) raw.getOrDefault("gyms", List.of());
        return gyms.stream()
                .map(g -> {
                    long gymId = asLong(g.get("gymId"));
                    return new DashboardTopGymBlock(
                            gymId,
                            asString(g.get("gymName")),
                            asString(g.get("category")),
                            asString(g.get("packages")),
                            asLong(g.get("qrCount")),
                            asLong(g.get("uniqueCustomers")),
                            homeCounts.getOrDefault(gymId, 0L),
                            asDouble(g.get("amount")),
                            asDoubleObj(g.get("trendPct"))
                    );
                })
                .toList();
    }

    private static LocalDate[] resolveRange(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate today = LocalDate.now(BAKU);
        LocalDate to = dateTo != null ? dateTo : today;
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(29);
        if (from.isAfter(to)) {
            return new LocalDate[]{to, from};
        }
        return new LocalDate[]{from, to};
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static Double asDoubleObj(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static boolean asBoolean(Object v) {
        return v instanceof Boolean b && b;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
