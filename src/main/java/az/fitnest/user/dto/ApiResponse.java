package az.fitnest.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.Map;

/**
 * Success → serialize bare {@code data} (mobile decodes root {@code user_id}/{@code first_name}).
 * Error → {@code {"error":…}}.
 * Explicit serializer (not {@code @JsonValue} on the record) — Jackson record handling ignored
 * {@code @JsonValue} and briefly shipped wrapped {@code {data,error}} which emptied iOS profiles.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(using = ApiResponse.Serializer.class)
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

    public static final class Serializer extends JsonSerializer<ApiResponse<?>> {
        @Override
        public void serialize(ApiResponse<?> value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            if (value.error() != null) {
                gen.writeObject(Map.of("error", value.error()));
                return;
            }
            if (value.data() == null) {
                gen.writeObject(Map.of());
                return;
            }
            gen.writeObject(value.data());
        }
    }
}
