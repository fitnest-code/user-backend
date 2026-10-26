package az.fitnest.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal user identity projection for internal service-to-service enrichment.
 * Deliberately excludes profile details (BMI, goals, images) — consumers only need
 * who the user is for labelling rows.
 */
@Schema(description = "Daxili servislər üçün istifadəçi kimlik xülasəsi")
public record InternalUserSummary(
        Long userId,
        String firstName,
        String lastName,
        String mobile,
        String email
) {
}
