package az.fitnest.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "İstifadəçi statistikası")
public record UserStatisticsResponse(
    @Schema(description = "Ümumi istifadəçi sayı", example = "1000")
    long totalUsers,

    @Schema(description = "Abunəliyinin bitməsinə son 7 gün qalmış istifadəçilərin sayı", example = "50")
    long usersWithLast7Days,

    @Schema(description = "Abunəliyi bitmiş istifadəçilərin sayı", example = "200")
    long finishedSubscriptions,

    @Schema(description = "Aktiv abunəliyi olan istifadəçilərin sayı", example = "700")
    long activeSubscriptions,

    @Schema(description = "Dondurulmuş abunəliyi olan istifadəçilərin sayı", example = "50")
    long frozenSubscriptions,

    @Schema(description = "Aktiv və ya dondurulmuş abunəliyi olan unikal istifadəçilərin sayı", example = "750")
    long activeOrFrozenSubscriptions
) {}
