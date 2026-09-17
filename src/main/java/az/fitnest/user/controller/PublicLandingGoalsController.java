package az.fitnest.user.controller;

import az.fitnest.user.dto.ApiResponse;
import az.fitnest.user.dto.ErrorResponse;
import az.fitnest.user.dto.response.GoalItemResponse;
import az.fitnest.user.service.GoalReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/landing/goals")
@RequiredArgsConstructor
@Tag(name = "Landing Public Goals", description = "Unauthenticated goal references for the website. Mobile should keep using /api/v1/goals.")
public class PublicLandingGoalsController {

    private static final CacheControl JSON_CACHE = CacheControl
            .maxAge(Duration.ofMinutes(2))
            .cachePublic()
            .staleWhileRevalidate(Duration.ofMinutes(2));

    private final GoalReferenceService goalReferenceService;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Public goals", description = "Localized goal list for the landing site. Pass language=AZ|EN|RU, or Accept-Language.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hədəflər uğurla əldə edildi", content = @Content(schema = @Schema(implementation = GoalItemResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<GoalItemResponse>>> getPublicGoals(
            @Parameter(description = "Target language: AZ, EN, or RU") @RequestParam(value = "language", required = false) String language) {
        return ResponseEntity.ok()
                .cacheControl(JSON_CACHE)
                .header(HttpHeaders.VARY, "Accept-Language")
                .body(ApiResponse.success(goalReferenceService.getPublicGoals(language)));
    }

    @GetMapping("/{code}")
    @SecurityRequirements
    @Operation(summary = "Public goal by code", description = "Single localized goal for the landing site.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hədəf tapıldı",
                    content = @Content(schema = @Schema(implementation = GoalItemResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Hədəf tapılmadı",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<GoalItemResponse>> getPublicGoalByCode(
            @Parameter(description = "Hədəfin unikal kodu (məsələn, LOSE_WEIGHT)") @PathVariable String code,
            @Parameter(description = "Target language: AZ, EN, or RU") @RequestParam(value = "language", required = false) String language) {
        return ResponseEntity.ok()
                .cacheControl(JSON_CACHE)
                .header(HttpHeaders.VARY, "Accept-Language")
                .body(ApiResponse.success(goalReferenceService.getPublicGoalByCode(code, language)));
    }

    @GetMapping(value = "/images/{fsId}")
    @SecurityRequirements
    @Operation(summary = "Public goal image", description = "Streams a goal image without authentication.")
    public ResponseEntity<byte[]> streamPublicGoalImage(@PathVariable String fsId) {
        byte[] data = goalReferenceService.downloadGoalImage(fsId);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(detectImageMediaType(data))
                .body(data);
    }

    private static MediaType detectImageMediaType(byte[] data) {
        if (data.length >= 8
                && data[0] == (byte) 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47) {
            return MediaType.IMAGE_PNG;
        }
        if (data.length >= 3
                && data[0] == (byte) 0xFF
                && data[1] == (byte) 0xD8
                && data[2] == (byte) 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        if (data.length >= 12
                && data[0] == 0x52
                && data[1] == 0x49
                && data[2] == 0x46
                && data[3] == 0x46) {
            return MediaType.parseMediaType("image/webp");
        }
        String head = new String(data, 0, Math.min(data.length, 256), StandardCharsets.UTF_8).trim();
        if (head.startsWith("<svg") || head.startsWith("<?xml") || head.contains("<svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
