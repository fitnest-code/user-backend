package az.fitnest.user.controller;

import az.fitnest.user.dto.response.CustomerGrowthResponse;
import az.fitnest.user.dto.response.KpiSummaryResponse;
import az.fitnest.user.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: nijataghayev
 */

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(
        name = "Dashboard Admin",
        description = "Dashboard statistikaları və analitik məlumatlar. ADMIN / SUPER_ADMIN / OWNER."
)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OWNER')")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "KPI Summary məlumatlarını gətir",
            description = "Dashboard üçün əsas KPI statistikalarını qaytarır."
    )
    @GetMapping("/kpi-summary")
    public ResponseEntity<KpiSummaryResponse> getKpiSummary() {

        return ResponseEntity.ok(dashboardService.getKpiSummary());
    }

    @Operation(
            summary = "Customer growth statistikasını gətir",
            description = "İstifadəçi artım statistikasını period üzrə qaytarır."
    )
    @GetMapping("/customer-growth")
    public ResponseEntity<CustomerGrowthResponse> getCustomerGrowth(

            @Parameter(
                    description = "Statistika periodu",
                    schema = @Schema(
                            allowableValues = {
                                    "DAILY",
                                    "WEEKLY",
                                    "MONTHLY",
                                    "YEARLY"
                            }
                    ),
                    example = "MONTHLY"
            )
            @RequestParam(defaultValue = "MONTHLY") String period
    ) {

        return ResponseEntity.ok(dashboardService.getCustomerGrowth(period));
    }
}