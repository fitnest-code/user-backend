package az.fitnest.user.service.dashboard;

import az.fitnest.user.client.dashboard.DashboardSiblingClient;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardGymRow;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardIncomeBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardObligationsBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardPackageCardBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardSubscriptionsBlock;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse.DashboardTopGymBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardV2Service {

    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    private final DashboardSiblingClient siblingClient;

    @Cacheable(cacheNames = "dashboard-v2-summary", key = "{#dateFrom, #dateTo}", sync = true)
    public DashboardSummaryResponse getSummary(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate[] range = resolveRange(dateFrom, dateTo);
        LocalDate from = range[0];
        LocalDate to = range[1];

        // Sibling RestClient needs request-scoped identity headers — run on the caller thread.
        DashboardIncomeBlock income = null;
        DashboardObligationsBlock obligations = null;
        DashboardSubscriptionsBlock subscriptions = null;
        List<DashboardTopGymBlock> topGyms = List.of();
        List<String> blockErrors = new ArrayList<>();

        try {
            income = mapIncome(siblingClient.fetchIncomeSummary(from, to));
        } catch (Exception e) {
            log.warn("dashboard income block failed: {}", e.getMessage());
            blockErrors.add("income");
        }
        try {
            obligations = mapObligations(siblingClient.fetchGymObligations(from, to));
        } catch (Exception e) {
            log.warn("dashboard obligations block failed: {}", e.getMessage());
            blockErrors.add("obligations");
        }
        try {
            subscriptions = mapSubscriptions(siblingClient.fetchSubscriptionSummary(from, to));
        } catch (Exception e) {
            log.warn("dashboard subscriptions block failed: {}", e.getMessage());
            blockErrors.add("subscriptions");
        }
        try {
            topGyms = mapTopGyms(siblingClient.fetchTopGyms(from, to, 10));
        } catch (Exception e) {
            log.warn("dashboard top-gyms block failed: {}", e.getMessage());
            blockErrors.add("topGyms");
        }

        return new DashboardSummaryResponse(
                income,
                obligations,
                subscriptions,
                topGyms,
                List.copyOf(blockErrors),
                from.toString(),
                to.toString()
        );
    }

    private static DashboardIncomeBlock mapIncome(Map<String, Object> raw) {
        return new DashboardIncomeBlock(
                asDouble(raw.get("totalIncome")),
                asDoubleObj(raw.get("incomeDeltaPct")),
                asBoolean(raw.get("incomePositiveTrend")),
                asDouble(raw.get("refundAmount")),
                asDouble(raw.get("pendingBnplAmount"))
        );
    }

    @SuppressWarnings("unchecked")
    private static DashboardObligationsBlock mapObligations(Map<String, Object> raw) {
        List<Map<String, Object>> gyms = (List<Map<String, Object>>) raw.getOrDefault("gyms", List.of());
        List<DashboardGymRow> rows = gyms.stream()
                .map(g -> new DashboardGymRow(
                        asLong(g.get("gymId")),
                        asString(g.get("gymName")),
                        asLong(g.get("qrCount")),
                        asDouble(g.get("amount"))))
                .toList();
        return new DashboardObligationsBlock(asDouble(raw.get("totalObligation")), rows);
    }

    @SuppressWarnings("unchecked")
    private static DashboardSubscriptionsBlock mapSubscriptions(Map<String, Object> raw) {
        List<Map<String, Object>> cards = (List<Map<String, Object>>) raw.getOrDefault("packageCards", List.of());
        List<DashboardPackageCardBlock> packageCards = cards.stream()
                .map(c -> new DashboardPackageCardBlock(
                        asLong(c.get("packageId")),
                        asString(c.get("packageName")),
                        asLong(c.get("activeCount")),
                        asLong(c.get("frozenCount"))))
                .toList();
        return new DashboardSubscriptionsBlock(
                asLong(raw.get("activeCount")),
                asLong(raw.get("frozenCount")),
                asLong(raw.get("endedInPeriod")),
                asLong(raw.get("renewedInPeriod")),
                asDoubleObj(raw.get("activeDeltaPct")),
                asBoolean(raw.get("activePositiveTrend")),
                packageCards
        );
    }

    @SuppressWarnings("unchecked")
    private static List<DashboardTopGymBlock> mapTopGyms(Map<String, Object> raw) {
        List<Map<String, Object>> gyms = (List<Map<String, Object>>) raw.getOrDefault("gyms", List.of());
        return gyms.stream()
                .map(g -> new DashboardTopGymBlock(
                        asLong(g.get("gymId")),
                        asString(g.get("gymName")),
                        asLong(g.get("qrCount")),
                        asDouble(g.get("amount"))))
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
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private static Double asDoubleObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static boolean asBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
