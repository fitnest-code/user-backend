package az.fitnest.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Success responses serialize as the bare {@code data} payload (mobile iOS/Android decode
 * root fields like {@code user_id}/{@code first_name}). Errors serialize as {@code {"error":…}}.
 * Do not replace {@link JsonValue} with explicit {@code data}/{@code error} properties — that
 * wraps every /me body and silently empties client profiles (regression from Oct 2026).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    T data,
    ApiError error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(ApiError apiError) {
        return new ApiResponse<>(null, apiError);
    }

    @JsonValue
    public Object asJson() {
        if (error != null) {
            return Map.of("error", error);
        }
        return data != null ? data : Map.of();
    }
}
