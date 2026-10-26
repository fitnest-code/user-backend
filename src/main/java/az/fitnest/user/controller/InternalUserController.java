package az.fitnest.user.controller;

import az.fitnest.user.dto.request.InternalUserBatchRequest;
import az.fitnest.user.dto.response.InternalUserSummary;
import az.fitnest.user.service.InternalUserDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service-to-service user directory. Reached only from inside the cluster
 * (gateway blocks /internal/), authenticated as ROLE_INTERNAL by FitnestSecurityFilter.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal User Directory", description = "Servislər arası istifadəçi kimlik axtarışı")
public class InternalUserController {

    private final InternalUserDirectoryService internalUserDirectoryService;

    @Operation(summary = "Batch istifadəçi kimliyi", description = "Verilən userId-lər üçün ad, soyad, telefon və e-poçt qaytarır (tək gRPC çağırışı ilə).")
    @PostMapping("/batch")
    public ResponseEntity<List<InternalUserSummary>> getUsers(@Valid @RequestBody InternalUserBatchRequest request) {
        return ResponseEntity.ok(internalUserDirectoryService.getUsers(request.userIds()));
    }

    @Operation(summary = "İstifadəçi axtarışı", description = "ID, ad, soyad, e-poçt və ya telefon üzrə uyğun userId-ləri qaytarır.")
    @GetMapping("/search")
    public ResponseEntity<Map<String, List<Long>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(Map.of("userIds", internalUserDirectoryService.searchUserIds(q, limit)));
    }
}
