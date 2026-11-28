package az.fitnest.user.service.dashboard;

import az.fitnest.user.client.DevicePlatformGrpcClient;
import az.fitnest.user.client.IdentityGrpcClient;
import az.fitnest.user.client.dashboard.DashboardSiblingClient;
import az.fitnest.user.dto.PaginatedResponse;
import az.fitnest.user.dto.dashboard.DashboardCustomerItem;
import az.fitnest.user.dto.dashboard.DashboardCustomerPage;
import az.fitnest.user.dto.response.AdminUserResponse;
import az.fitnest.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parallel customers list for the new dashboard. Does not change {@link AdminUserResponse}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardCustomerService {

    private final AdminUserService adminUserService;
    private final IdentityGrpcClient identityGrpcClient;
    private final DevicePlatformGrpcClient devicePlatformGrpcClient;
    private final DashboardSiblingClient siblingClient;

    @Cacheable(cacheNames = "dashboard-v2-customers", key = "{#page,#size,#packageId,#packageDuration,#subscriptionStatus,#sort,#search,#platform,#registrationFrom,#registrationTo,#purchaseFrom,#purchaseTo}")
    public DashboardCustomerPage list(
            int page,
            int size,
            Long packageId,
            Integer packageDuration,
            String subscriptionStatus,
            String sort,
            String search,
            String platform,
            LocalDate registrationFrom,
            LocalDate registrationTo,
            LocalDate purchaseFrom,
            LocalDate purchaseTo) {

        PaginatedResponse<AdminUserResponse> base = adminUserService.getAllUsers(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)),
                packageId,
                packageDuration,
                subscriptionStatus,
                sort,
                search,
                List.of("ROLE_USER")
        );

        List<AdminUserResponse> items = base.items() != null ? base.items() : List.of();
        List<Long> userIds = items.stream().map(AdminUserResponse::id).toList();

        Map<Long, String> registrationDates = loadRegistrationDates(userIds);
        Map<Long, String> platforms = loadPlatforms(userIds);
        Map<Long, String> purchaseDates = loadPurchaseDates(userIds);

        List<DashboardCustomerItem> enriched = items.stream()
                .map(u -> new DashboardCustomerItem(
                        u.id(),
                        u.fullName(),
                        u.phoneNumber(),
                        u.email(),
                        u.userStatus(),
                        u.subscriptionStatus(),
                        u.role(),
                        u.gymName(),
                        platforms.get(u.id()),
                        registrationDates.get(u.id()),
                        purchaseDates.get(u.id())
                ))
                .filter(c -> matchesPlatform(c, platform))
                .filter(c -> matchesDate(c.registrationDate(), registrationFrom, registrationTo))
                .filter(c -> matchesDate(c.purchaseDate(), purchaseFrom, purchaseTo))
                .collect(Collectors.toList());

        return new DashboardCustomerPage(enriched, base.total(), base.page(), base.pageSize());
    }

    private Map<Long, String> loadRegistrationDates(List<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        try {
            for (var user : identityGrpcClient.getUsersByIds(userIds)) {
                if (user.getCreatedAt() != null && !user.getCreatedAt().isBlank()) {
                    map.put(user.getUserId(), user.getCreatedAt());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load registration dates: {}", e.getMessage());
        }
        return map;
    }

    private Map<Long, String> loadPlatforms(List<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        for (Long userId : userIds) {
            try {
                String platform = devicePlatformGrpcClient.getUserPlatform(userId);
                if (platform != null && !platform.isBlank()) {
                    map.put(userId, platform);
                }
            } catch (Exception e) {
                log.debug("Platform lookup failed for {}: {}", userId, e.getMessage());
            }
        }
        return map;
    }

    private Map<Long, String> loadPurchaseDates(List<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        for (Map<String, Object> row : siblingClient.fetchPurchaseDates(userIds)) {
            Object userId = row.get("userId");
            Object purchaseDate = row.get("purchaseDate");
            if (userId instanceof Number n && purchaseDate != null) {
                map.put(n.longValue(), String.valueOf(purchaseDate));
            }
        }
        return map;
    }

    private static boolean matchesPlatform(DashboardCustomerItem c, String platform) {
        if (platform == null || platform.isBlank()) {
            return true;
        }
        return c.platform() != null && c.platform().equalsIgnoreCase(platform.trim());
    }

    private static boolean matchesDate(String iso, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (iso == null || iso.isBlank()) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(iso.length() >= 10 ? iso.substring(0, 10) : iso);
            if (from != null && date.isBefore(from)) {
                return false;
            }
            if (to != null && date.isAfter(to)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
