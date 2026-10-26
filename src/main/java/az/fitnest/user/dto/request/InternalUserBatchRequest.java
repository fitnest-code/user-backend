package az.fitnest.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Batch istifadəçi sorğusu")
public record InternalUserBatchRequest(
        @NotEmpty(message = "userIds boş ola bilməz")
        @Size(max = 200, message = "Bir sorğuda maksimum 200 istifadəçi")
        List<Long> userIds
) {
}
