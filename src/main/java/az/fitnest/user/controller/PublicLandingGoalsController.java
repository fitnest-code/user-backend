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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    @GetMapping(value = "/images/{fsId}", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "image/svg+xml"})
    @SecurityRequirements
    @Operation(summary = "Public goal image", description = "Streams a goal image without authentication.")
    public ResponseEntity<StreamingResponseBody> streamPublicGoalImage(@PathVariable String fsId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(goalReferenceService.streamGoalImage(fsId));
    }
}
