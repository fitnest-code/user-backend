package az.fitnest.user.dto.dashboard;

import java.util.List;

/**
 * Full admin home dashboard payload shaped for the Maliyyə / Müştərilər / Abunəliklər / QR mockups.
 */
public record DashboardSummaryResponse(
        DashboardFinanceBlock finance,
        DashboardCustomersBlock customers,
        DashboardSubscriptionsBlock subscriptions,
        List<DashboardPackageCardBlock> packageCards,
        DashboardQrBlock qr,
        List<DashboardTopGymBlock> topGyms,
        List<String> blockErrors,
        String dateFrom,
        String dateTo
) {
    public record DashboardFinanceBlock(
            double totalIncome,
            Double incomeDeltaPct,
            boolean incomePositiveTrend,
            double totalObligation,
            Double obligationDeltaPct,
            boolean obligationPositiveTrend,
            double netBalance,
            Double netDeltaPct,
            boolean netPositiveTrend,
            double marginPct,
            double refundAmount,
            double pendingBnplAmount,
            List<DashboardGymRow> obligationGyms
    ) {}

    public record DashboardGymRow(Long gymId, String gymName, long qrCount, double amount) {}

    public record DashboardCustomersBlock(
            long totalCustomers,
            Double totalDeltaPct,
            long activeCustomers,
            Double activeDeltaPct,
            long newRegistrations,
            Double newDeltaPct,
            double conversionPct,
            Double conversionDeltaPp
    ) {}

    public record DashboardSubscriptionsBlock(
            long last7DaysCount,
            long activeCount,
            long frozenCount,
            long endedInPeriod,
            long renewedInPeriod,
            Double activeDeltaPct,
            boolean activePositiveTrend
    ) {}

    public record DashboardPackageCardBlock(
            Long packageId,
            String packageName,
            long activeCount,
            long frozenCount,
            long last7DaysCount,
            long endingIn30DaysCount,
            double sharePct,
            Double revenue
    ) {}

    public record DashboardQrBlock(
            long totalScans,
            Double totalDeltaPct,
            long lightScans,
            Double lightDeltaPct,
            long darkScans,
            Double darkDeltaPct
    ) {}

    public record DashboardTopGymBlock(
            Long gymId,
            String gymName,
            String category,
            String packages,
            long qrCount,
            long uniqueCustomers,
            long primaryGymSelectors,
            double amount,
            Double trendPct
    ) {}
}
