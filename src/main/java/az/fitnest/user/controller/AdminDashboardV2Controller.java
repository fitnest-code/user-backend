package az.fitnest.user.controller;

import az.fitnest.user.dto.dashboard.DashboardCustomerPage;
import az.fitnest.user.dto.dashboard.DashboardSavedViewRequest;
import az.fitnest.user.dto.dashboard.DashboardSavedViewResponse;
import az.fitnest.user.dto.dashboard.DashboardSummaryResponse;
import az.fitnest.user.service.dashboard.AdminDashboardV2Service;
import az.fitnest.user.service.dashboard.DashboardCustomerService;
import az.fitnest.user.service.dashboard.DashboardSavedViewService;
import az.fitnest.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Parallel admin dashboard API surface. Leaves {@link DashboardController} kpi-summary /
 * customer-growth unchanged for production safety.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard V2", description = "Non-breaking dashboard summary, customers, saved views")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OWNER')")
public class AdminDashboardV2Controller {

    private final AdminDashboardV2Service adminDashboardV2Service;
    private final DashboardCustomerService dashboardCustomerService;
    private final DashboardSavedViewService dashboardSavedViewService;

    @Operation(summary = "Aggregated dashboard summary (income, obligations, subscriptions, top gyms)")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(adminDashboardV2Service.getSummary(dateFrom, dateTo));
    }

    @Operation(summary = "Dashboard customers list with platform / registration / purchase columns")
    @GetMapping("/customers")
    public ResponseEntity<DashboardCustomerPage> customers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long packageId,
            @RequestParam(required = false) Integer packageDuration,
            @RequestParam(required = false) String subscriptionStatus,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registrationFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registrationTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo) {
        return ResponseEntity.ok(dashboardCustomerService.list(
                page, size, packageId, packageDuration, subscriptionStatus, sort, search,
                platform, registrationFrom, registrationTo, purchaseFrom, purchaseTo));
    }

    @Operation(summary = "List saved dashboard filter views for the current admin")
    @GetMapping("/views")
    public ResponseEntity<List<DashboardSavedViewResponse>> listViews() {
        return ResponseEntity.ok(dashboardSavedViewService.list(requireAdminId()));
    }

    @Operation(summary = "Create a saved dashboard filter view")
    @PostMapping("/views")
    public ResponseEntity<DashboardSavedViewResponse> createView(@Valid @RequestBody DashboardSavedViewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dashboardSavedViewService.create(requireAdminId(), request));
    }

    @Operation(summary = "Delete a saved dashboard filter view")
    @DeleteMapping("/views/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteView(@PathVariable Long viewId) {
        dashboardSavedViewService.delete(requireAdminId(), viewId);
    }

    private static Long requireAdminId() {
        Long id = UserContext.getCurrentUserId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing admin identity");
        }
        return id;
    }
}
