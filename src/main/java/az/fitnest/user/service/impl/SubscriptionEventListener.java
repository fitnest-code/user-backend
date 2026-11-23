package az.fitnest.user.service.impl;

import az.fitnest.user.service.AdminUserService;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Lifecycle subscription events (assign/finish/revoke/…) change ACTIVE/FINISHED aggregates.
 * Freeze transitions arrive on {@code subscription-freeze-events} instead — see
 * {@link FreezeSubscriptionEventListener}.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionEventListener {

    /** Actions that change admin ACTIVE / FINISHED / LAST_7_DAYS counts. */
    private static final Set<String> STATS_INVALIDATING_ACTIONS = Set.of(
            "ASSIGNED",
            "FINISHED",
            "REVOKED",
            "CANCELLED",
            "EXPIRED",
            "UPGRADED");

    private final CacheManager cacheManager;
    private final AdminUserService adminUserService;

    @KafkaListener(topics = "subscription-events", groupId = "user-backend")
    public void handleSubscriptionEvent(Map<String, Object> event) {
        if (event == null) {
            return;
        }
        Long userId = parseUserId(event.get("userId"));
        if (userId != null) {
            Cache userMeCache = cacheManager.getCache("user_me");
            if (userMeCache != null) {
                userMeCache.evict(userId);
            }
        }

        String action = stringField(event, "action");
        if (action == null) {
            action = stringField(event, "eventType");
        }
        if (action != null && STATS_INVALIDATING_ACTIONS.contains(action.toUpperCase())) {
            adminUserService.invalidateListCaches();
            return;
        }
        // Check-ins and other noise: clear list cache only (prior behaviour), not statistics.
        Cache adminUsersCache = cacheManager.getCache("admin-users");
        if (adminUsersCache != null) {
            adminUsersCache.clear();
        }
    }

    private static Long parseUserId(Object userIdObj) {
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        if (userIdObj instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringField(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
