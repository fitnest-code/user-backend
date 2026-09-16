package az.fitnest.user.controller;

import java.util.Map;
import java.util.Collection;
import java.util.Arrays;
import java.util.Objects;

import az.fitnest.user.service.UserProfileService;
import az.fitnest.user.dto.*;
import az.fitnest.user.dto.request.*;
import az.fitnest.user.dto.response.*;
import az.fitnest.user.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import az.fitnest.user.client.CachedIdentityGrpcClient;
import az.fitnest.user.util.UserContext;
import az.fitnest.user.repository.TranslationRepository;
import az.fitnest.user.model.entity.Translation;
import org.springframework.security.access.prepost.PreAuthorize;
import az.fitnest.user.client.StorageGrpcClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "İstifadəçi profili, parametrləri və hesabını idarə etmək üçün ucluqlar")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final CachedIdentityGrpcClient cachedIdentityGrpcClient;
    private final TranslationRepository translationRepository;
    private final StorageGrpcClient storageGrpcClient;

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

    @Operation(summary = "İstifadəçi xülasəsini əldə edin", description = "İstifadəçinin profili və tərəqqisi haqqında qısa xülasə qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xülasə uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = SummaryResponse.class), examples = @ExampleObject(value = "{\"totalWorkouts\": 25, \"totalCalories\": 1500}")))
    })
    @GetMapping("/api/v1/me/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getUserSummary()));
    }

    @Operation(summary = "Cari istifadəçi profilini əldə edin", description = "Autentifikasiya olunmuş istifadəçinin tam profil təfərrüatlarını qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class), examples = @ExampleObject(value = "{\"user_id\": 1, \"first_name\": \"John\", \"last_name\": \"Doe\", \"email\": \"john.doe@example.com\", \"mobile\": \"+994500000000\", \"profile_image_url\": \"null\", \"current_subscription\": \"Bronze\", \"subscription_status\": \"ACTIVE\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Profil tapılmadı",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/v1/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getUserMe()));
    }

    @Operation(
            summary = "Cari istifadəçi profilini əldə edin (v2)",
            description = "Profil məlumatları ilə birlikdə Coin balansı, AZN ekvivalenti (admin spend rate qaydasına əsasən) və etibarlılıq tarixini qaytarır."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Profil uğurla əldə edildi",
                    content = @Content(
                            schema = @Schema(implementation = UserProfileV2Response.class),
                            examples = @ExampleObject(value = "{\"user_id\":1,\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"john.doe@example.com\",\"mobile\":\"+994500000000\",\"profile_image_url\":null,\"current_subscription\":\"Bronze\",\"subscription_status\":\"ACTIVE\",\"coin_balance\":320.00,\"coin_azn_equivalent\":32.00,\"coin_validity_date\":\"01.09.2027\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Profil tapılmadı",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/api/v2/me")
    public ResponseEntity<ApiResponse<UserProfileV2Response>> getMeV2() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getUserMeV2()));
    }

    @Operation(summary = "İstifadəçi profilini yeniləyin", description = "Autentifikasiya olunmuş istifadəçinin ad, soyad, e-poçt və mobil nömrəsini yeniləyir. Yalnız təqdim olunan sahələr yenilənəcək, digərləri dəyişməz qalacaq. Giriş məlumatları üzərində doğrulama aparılır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil uğurla yeniləndi",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class), examples = @ExampleObject(value = "{\"user_id\": 1, \"first_name\": \"John\", \"last_name\": \"Doe\", \"email\": \"john.doe@example.com\", \"mobile\": \"+994500000000\", \"profile_image_url\": \"null\", \"current_subscription\": \"Bronze\"}")))
    })
    @PutMapping("/api/v1/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.updateUserMe(request)));
    }

    @Operation(summary = "İstifadəçi məkanını yeniləyin", description = "İstifadəçinin cari şəhər və ölkəsini yeniləyir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Məkan uğurla yeniləndi",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    })
    @PutMapping("/api/v1/me/location")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(@Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.updateMyLocation(request)));
    }

    @Operation(summary = "Bədən göstəricilərini yeniləyin", description = "İstifadəçinin boy, çəki və s. kimi fiziki göstəricilərini yeniləyir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bədən göstəriciləri uğurla yeniləndi", content = @Content(examples = @ExampleObject(value = "null")))
    })
    @PutMapping("/api/v1/me/body")
    public ResponseEntity<ApiResponse<Void>> updateBody(@Valid @RequestBody UpdateBodyRequest request) {
        userProfileService.updateBody(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Bədən göstəricilərini əldə edin", description = "İstifadəçinin fiziki göstəricilərini qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bədən göstəriciləri uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = BodyInfoResponse.class)))
    })
    @GetMapping("/api/v1/me/body")
    public ResponseEntity<ApiResponse<BodyInfoResponse>> getBody() {
        String userLanguage = getUserLanguage();
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getBodyInfo(userLanguage)));
    }

    @Operation(summary = "Profil şəklini yeniləyin", description = "İstifadəçi üçün yeni profil şəkli yükləyir və təyin edir.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = ProfileImageUploadRequest.class)))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil şəkli uğurla yeniləndi")
    })
    @PutMapping(value = "/api/v1/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProfileImage(@RequestPart("image") MultipartFile file) {
        userProfileService.updateProfileImage(file);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Profil şəklini silin", description = "İstifadəçinin profil şəklini silir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil şəkli uğurla silindi")
    })
    @DeleteMapping("/api/v1/me/profile-image")
    public ResponseEntity<Void> deleteProfileImage() {
        userProfileService.deleteProfileImage();
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "İstifadəçi hədəfini yeniləyin", description = "İstifadəçinin əsas fitnes və ya sağlamlıq hədəfini yeniləyir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hədəf uğurla yeniləndi", content = @Content(examples = @ExampleObject(value = "null")))
    })
    @PutMapping("/api/v1/me/goal")
    public ResponseEntity<ApiResponse<Void>> updateGoal(@Valid @RequestBody UpdateGoalsRequest request) {
        userProfileService.updateGoal(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "İstifadəçi hədəfini əldə edin", description = "İstifadəçinin cari hədəfini qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hədəf uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = GoalResponse.class)))
    })
    @GetMapping("/api/v1/me/goal")
    public ResponseEntity<ApiResponse<GoalResponse>> getGoal() {
        String userLanguage = getUserLanguage();
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getGoal(userLanguage)));
    }

    @Operation(summary = "İstifadəçi seçimlərini yeniləyin", description = "Dil və mövzu kimi tətbiq parametrlərini yeniləyir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Seçimlər uğurla yeniləndi", content = @Content(examples = @ExampleObject(value = "null")))
    })
    @PutMapping("/api/v1/me/preferences")
    public ResponseEntity<ApiResponse<Void>> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        userProfileService.updatePreferences(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "İstifadəçi dilini yeniləyin", description = "İstifadəçinin üstünlük verdiyi dili yeniləyir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dil uğurla yeniləndi", content = @Content(examples = @ExampleObject(value = "null")))
    })
    @PutMapping("/api/v1/me/language")
    public ResponseEntity<ApiResponse<Void>> updateLanguage(@Valid @RequestBody UpdateLanguageRequest request) {
        userProfileService.updateLanguage(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Mövcud dilləri əldə edin", description = "Tətbiqdə mövcud olan bütün dilləri qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dillər uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = LanguageDto.class)))
    })
    @GetMapping("/api/v1/me/languages")
    public ResponseEntity<ApiResponse<java.util.List<LanguageDto>>> getLanguages() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getAllLanguages()));
    }

    @Operation(summary = "Cari dili əldə edin", description = "İstifadəçinin seçdiyi cari dili qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cari dil uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = LanguageDto.class)))
    })
    @GetMapping("/api/v1/me/language")
    public ResponseEntity<ApiResponse<LanguageDto>> getCurrentLanguage() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getCurrentLanguage()));
    }

    @Operation(summary = "Quraşdırma statusunu əldə edin", description = "İstifadəçi profilinin quraşdırılmasının cari tərəqqisini qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quraşdırma statusu uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = SetupResponse.class)))
    })
    @GetMapping("/api/v1/me/setup")
    public ResponseEntity<ApiResponse<SetupResponse>> getSetup() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getSetupStatus()));
    }

    @Operation(summary = "İlkin profil quraşdırması", description = "İstifadəçi profilini tələb olunan ilkin detallarla quraşdırır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil quraşdırma mərhələsi uğurla tamamlandı",
                    content = @Content(schema = @Schema(implementation = SetupResponse.class)))
    })
    @PostMapping("/api/v1/me/setup")
    public ResponseEntity<ApiResponse<SetupResponse>> setupProfile(@Valid @RequestBody SetupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.setupProfile(request)));
    }

    @Operation(summary = "Profil quraşdırmasını tamamlayın", description = "İstifadəçi profilinin quraşdırılması prosesini yekunlaşdırır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quraşdırma uğurla tamamlandı",
                    content = @Content(schema = @Schema(implementation = CompleteSetupResponse.class)))
    })
    @PostMapping("/api/v1/me/setup/complete")
    public ResponseEntity<ApiResponse<CompleteSetupResponse>> completeSetup() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.completeSetup()));
    }

    @Operation(summary = "Profil quraşdırmasını keçin", description = "İstifadəçiyə profil quraşdırma prosesini keçməyə imkan verir.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quraşdırma uğurla keçildi",
                    content = @Content(schema = @Schema(implementation = CompleteSetupResponse.class)))
    })
    @PostMapping("/api/v1/me/setup/skip")
    public ResponseEntity<ApiResponse<CompleteSetupResponse>> skipSetup() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.skipSetup()));
    }

    @Operation(summary = "Fitnes səviyyəsini əldə edin", description = "İstifadəçinin cari fitnes səviyyəsini qaytarır.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Fitnes səviyyəsi uğurla əldə edildi",
                    content = @Content(schema = @Schema(implementation = FitnessLevelResponse.class)))
    })
    @GetMapping("/api/v1/me/fitness-level")
    public ResponseEntity<ApiResponse<FitnessLevelResponse>> getFitnessLevel() {
        String userLanguage = getUserLanguage();
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getFitnessLevel(userLanguage)));
    }

    @GetMapping("/api/v1/me/profile/images/{fsId}")
    public ResponseEntity<StreamingResponseBody> streamProfileImage(@PathVariable String fsId) {
        logger.info("streamProfileImage called for fsId: {}", fsId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("Authorization failed: not authenticated for fsId: {}", fsId);
            return ResponseEntity.status(403).build();
        }
        boolean hasRole = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> auth.equals("ROLE_USER") || auth.equals("ROLE_ADMIN") ||
                        auth.equals("USER") || auth.equals("ADMIN"));
        if (!hasRole) {
            logger.warn("Authorization failed: missing role for fsId: {}", fsId);
            return ResponseEntity.status(403).build();
        }
        boolean canAccess = storageGrpcClient.canAccessFile(fsId);
        if (!canAccess) {
            logger.warn("Storage access denied for fsId: {}", fsId);
            return ResponseEntity.status(403).build();
        }
        logger.info("Authorization passed for streaming profile image: {}", fsId);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(outputStream -> {
                    logger.info("Streaming profile image started for fsId: {}", fsId);
                    SecurityContext previous = SecurityContextHolder.getContext();
                    try {
                        SecurityContextHolder.setContext(securityContext);
                        try {
                            storageGrpcClient.downloadFile(fsId, response -> {
                                if (response.hasFileData()) {
                                    try {
                                        outputStream.write(response.getFileData().toByteArray());
                                    } catch (Exception e) {
                                        logger.error("Failed to stream file for fsId: {}", fsId, e);
                                    }
                                }
                            });
                            outputStream.flush();
                        } catch (Exception e) {
                            logger.error("Download stream failed for fsId: {}", fsId, e);
                        }
                    } finally {
                        SecurityContextHolder.setContext(previous);
                    }
                });
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null) return false;
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null || authorities.isEmpty()) return false;

        return Arrays.stream(roles)
                .filter(Objects::nonNull)
                .anyMatch(role -> authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(auth -> auth.equals(role) || auth.equals("ROLE_" + role)));
    }

    private String getUserLanguage() {
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            try {
                az.fitnest.user.dto.response.IdentityUserResponse user = cachedIdentityGrpcClient.getUserById(userId);
                String language = user.language();
                if (language != null && !language.isEmpty()) {
                    return language.toUpperCase();
                }
            } catch (Exception e) {
            }
        }
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = 
                        ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage != null && !acceptLanguage.trim().isEmpty()) {
                    String localeLang = org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage()
                            .toUpperCase();
                    if (localeLang.equals("EN") || localeLang.equals("RU") || localeLang.equals("AZ")) {
                        return localeLang;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "AZ";
    }
}
