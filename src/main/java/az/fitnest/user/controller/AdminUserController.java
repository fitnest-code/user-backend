package az.fitnest.user.controller;

import az.fitnest.user.dto.PaginatedResponse;
import az.fitnest.user.dto.response.AdminUserResponse;
import az.fitnest.user.dto.response.UserStatisticsResponse;
import az.fitnest.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "İstifadəçi Admin Kontrolörü", description = "İstifadəçi idarəetməsi və statistika üçün endpointlər")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Bütün istifadəçiləri detalları ilə gətir", description = "Status və abunə məlumatları daxil olmaqla, səhifələnmiş istifadəçi siyahısını qaytarır. İstifadəçi ID-si, tam adı, e-poçt ünvanı və ya telefon nömrəsi ilə axtarışı dəstəkləyir.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PaginatedResponse<AdminUserResponse>> getAllUsers(
            @Parameter(description = "Səhifə nömrəsi (0-dan indekslənir)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Hər səhifədəki elementlərin sayı", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Abunə paketi ID-si ilə filtrlə. Bütün paketlər üçün buraxın və ya boş qoyun.") @RequestParam(required = false) Long packageId,
            @Parameter(description = "Paket müddəti (aylarla) ilə filtrlə. Bütün müddətlər üçün buraxın və ya boş qoyun.", example = "1") @RequestParam(required = false) Integer packageDuration,
            @Parameter(description = "Abunə statusu ilə filtrlə. Bütün statuslar üçün buraxın və ya boş qoyun.", schema = @Schema(allowableValues = {
                    "ACTIVE", "FINISHED", "FROZEN",
                    "LAST_7_DAYS" })) @RequestParam(required = false) String subscriptionStatus,
            @Parameter(description = "Nəticələr üçün sıralama qaydası. Dəyərlər: "
                    + "newest - Yeni əlavə edilmiş, "
                    + "name_asc - Ad : A-Z, "
                    + "name_desc - Ad : Z-A, "
                    + "finishDate_asc - Abunə bitmə tarixi (ən tez bitən birinci), "
                    + "finishDate_desc - Abunə bitmə tarixi (ən son bitən birinci), "
                    + "registrationDate_desc - Qeydiyyat tarixi (yeni → köhnə), "
                    + "registrationDate_asc - Qeydiyyat tarixi (köhnə → yeni)", schema = @Schema(allowableValues = {
                            "newest", "name_asc", "name_desc",
                            "finishDate_asc", "finishDate_desc",
                            "registrationDate_desc",
                            "registrationDate_asc" })) @RequestParam(required = false) String sort,
            @Parameter(description = "İstifadəçi ID-si, tam adı, e-poçt ünvanı və ya telefon nömrəsi ilə axtar") @RequestParam(required = false) String search,
            @Parameter(description = "İstifadəçi rolları ilə filtrlə") @RequestParam(required = false) java.util.List<String> roles) {
        return ResponseEntity.ok(adminUserService.getAllUsers(PageRequest.of(page, size), packageId, packageDuration,
                subscriptionStatus, sort, search, roles));
    }

    @Operation(summary = "İstifadəçi statistikasını gətir", description = "Ümumi istifadəçi, aktiv/dondurulmuş, bitmiş və tezliklə bitəcək abunəliklərin sayını qaytarır.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics")
    public ResponseEntity<UserStatisticsResponse> getUserStatistics() {
        return ResponseEntity.ok(adminUserService.getUserStatistics());
    }

    @Operation(summary = "İstifadəçi detallarını gətir", description = "İstifadəçinin bütün detallarını (ad, soyad, qeydiyyat tarixi, platform, telefon, email, boy, çəki, BMI, Coin balansı və s.) qaytarır.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}")
    public ResponseEntity<az.fitnest.user.dto.response.AdminUserDetailResponse> getUserDetail(
            @Parameter(description = "İstifadəçi ID-si", example = "123") @PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserDetail(userId));
    }
}
