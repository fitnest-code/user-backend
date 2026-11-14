package az.fitnest.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of the internal user directory. Used by other services that need to
 * iterate a large audience (for example, promo campaigns targeting a phone prefix).
 */
@Schema(description = "Daxili istifadəçi kataloqu səhifəsi")
public record InternalUserPageResponse(
        List<InternalUserSummary> users,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
