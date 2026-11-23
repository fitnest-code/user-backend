package az.fitnest.user.service.impl;

import az.fitnest.user.service.AdminUserService;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Freeze/unfreeze lives in order-backend; admin "Dondurulmuş Status" is cached in this service.
 * Consume the same outbox topic notifications already use so stats drop without {@code ?refresh=true}
 * on every page load (and without coupling every freeze writer to an HTTP invalidate call).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FreezeSubscriptionEventListener {

    private static final Set<String> STATS_INVALIDATING_TYPES = Set.of(
            "freeze_started",
            "subscription_frozen",
            "freeze_completed",
            "subscription_unfrozen",
            "freeze_ended_early",
            "freeze_blocked");

    private final AdminUserService adminUserService;

    @KafkaListener(
            topics = "subscription-freeze-events",
            groupId = "user-backend-freeze-stats",
            properties = {"spring.json.value.default.type=java.util.Map"})
    public void onFreezeEvent(Map<String, Object> event) {
        if (event == null) {
            return;
        }
        Object typeObj = event.get("eventType");
        if (!(typeObj instanceof String eventType) || !STATS_INVALIDATING_TYPES.contains(eventType)) {
            return;
        }
        try {
            adminUserService.invalidateListCaches();
            log.debug("Invalidated admin user-statistics after freeze event {}", eventType);
        } catch (Exception e) {
            log.warn("Failed to invalidate admin caches after freeze event {}: {}", eventType, e.toString());
        }
    }
}
