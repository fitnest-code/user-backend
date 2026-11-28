package az.fitnest.user.dto.dashboard;

import java.util.List;

public record DashboardCustomerPage(
        List<DashboardCustomerItem> content,
        long totalElements,
        int page,
        int size
) {}
