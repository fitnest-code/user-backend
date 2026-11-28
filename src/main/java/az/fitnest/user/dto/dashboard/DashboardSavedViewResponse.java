package az.fitnest.user.dto.dashboard;

public record DashboardSavedViewResponse(
        Long id,
        String name,
        String filtersJson,
        String createdAt
) {}
