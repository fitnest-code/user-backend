package az.fitnest.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@Schema(description = "İstifadəçinin ətraflı məlumatları (Admin üçün)")
public record AdminUserDetailResponse(
    @Schema(description = "İstifadəçi ID-si", example = "123")
    Long userId,

    @Schema(description = "Tam Ad (Ad + Soyad)", example = "Kamal Əliyev")
    String fullName,

    @Schema(description = "Qeydiyyat tarixi", example = "2023-10-25")
    String registrationDate,

    @Schema(description = "Son istifadə olunan platform/cihaz", example = "iOS")
    String platform,

    @Schema(description = "Mobil nömrə", example = "0501234567")
    String phoneNumber,

    @Schema(description = "Email", example = "kamal@fitnest.az")
    String email,

    @Schema(description = "Doğum tarixi", example = "1990-01-01")
    LocalDate birthDate,

    @Schema(description = "Məqsəd", example = "Çəki itirmək")
    String goalTitle,

    @Schema(description = "Boy (sm)", example = "180.0")
    Double height,

    @Schema(description = "Çəki (kq)", example = "85.0")
    Double weight,

    @Schema(description = "BMI İndeksi", example = "26.2")
    Double bmiIndex,

    @Schema(description = "Rol", example = "ROLE_USER")
    String role,

    @Schema(description = "Coin balansı", example = "320.00")
    BigDecimal coinBalance,

    @Schema(description = "Coin-in AZN ekvivalenti", example = "32.00")
    BigDecimal coinAznEquivalent
) {}
