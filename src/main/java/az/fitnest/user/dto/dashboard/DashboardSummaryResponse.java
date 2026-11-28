package az.fitnest.user.dto.dashboard;

import java.util.List;

/**
 * Aggregated dashboard summary. Individual blocks may be null when a sibling service fails
 * ({@code blockErrors} lists which blocks failed).
 */
public record DashboardSummaryResponse(
        DashboardIncomeBlock income,
        DashboardObligationsBlock obligations,
        DashboardSubscriptionsBlock subscriptions,
        List<DashboardTopGymBlock> topGyms,
        List<String> blockErrors,
        String dateFrom,
        String dateTo
) {
    public record DashboardIncomeBlock(
            double totalIncome,
            Double incomeDeltaPct,
            boolean incomePositiveTrend,
            double refundAmount,
            double pendingBnplAmount
    ) {}

    public record DashboardObligationsBlock(
            double totalObligation,
            List<DashboardGymRow> gyms
    ) {}

    public record DashboardGymRow(Long gymId, String gymName, long qrCount, double amount) {}

    public record DashboardSubscriptionsBlock(
            long activeCount,
            long frozenCount,
            long endedInPeriod,
            long renewedInPeriod,
            Double activeDeltaPct,
            boolean activePositiveTrend,
            List<DashboardPackageCardBlock> packageCards
    ) {}

    public record DashboardPackageCardBlock(
            Long packageId,
            String packageName,
            long activeCount,
            long frozenCount
    ) {}

    public record DashboardTopGymBlock(Long gymId, String gymName, long qrCount, double amount) {}
}
