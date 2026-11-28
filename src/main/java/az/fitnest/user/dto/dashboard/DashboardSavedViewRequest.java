package az.fitnest.user.dto.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DashboardSavedViewRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String filtersJson
) {}
