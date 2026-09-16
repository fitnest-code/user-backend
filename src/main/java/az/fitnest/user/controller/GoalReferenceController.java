package az.fitnest.user.controller;

import az.fitnest.user.service.GoalReferenceService;
import az.fitnest.user.dto.response.GoalItemResponse;
import az.fitnest.user.dto.ApiResponse;
import az.fitnest.user.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@Tag(name = "Goal Management", description = "Public health and fitness goal references. Auth is not required.")
public class GoalReferenceController {

    private final GoalReferenceService goalReferenceService;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "List all goals", description = "Public. Returns every goal reference. Use Accept-Language (AZ, EN, RU) for translated titles.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hədəflər uğurla əldə edildi", content = @Content(schema = @Schema(implementation = GoalItemResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<GoalItemResponse>>> getAllGoals() {
        return ResponseEntity.ok(ApiResponse.success(goalReferenceService.getAllGoals()));
    }

    @GetMapping("/{code}")
    @SecurityRequirements
    @Operation(summary = "Get a goal by code", description = "Public. Returns a single goal reference by its unique code.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hədəf tapıldı",
                    content = @Content(schema = @Schema(implementation = GoalItemResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Hədəf tapılmadı",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<GoalItemResponse>> getGoalByCode(
            @Parameter(description = "Hədəfin unikal kodu (məsələn, LOSE_WEIGHT)") @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(goalReferenceService.getGoalByCode(code)));
    }

    @GetMapping(value = "/images/{fsId}", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "image/svg+xml"})
    @SecurityRequirements
    @Operation(summary = "Stream a goal image", description = "Public. Streams the image associated with a goal.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Şəkil yayımı başladı"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Şəkil tapılmadı")
    })
    public ResponseEntity<StreamingResponseBody> streamGoalImage(@PathVariable String fsId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(goalReferenceService.streamGoalImage(fsId));
    }
}
