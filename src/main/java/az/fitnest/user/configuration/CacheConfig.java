package az.fitnest.user.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.TimeUnit;

/**
 * @author: nijataghayev
 */

@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        org.springframework.cache.support.SimpleCacheManager manager = new org.springframework.cache.support.SimpleCacheManager();
        manager.setCaches(java.util.Arrays.asList(
                new org.springframework.cache.caffeine.CaffeineCache("dashboard-kpi",
                        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build()),
                new org.springframework.cache.caffeine.CaffeineCache("dashboard-growth",
                        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build()),
                // Evicted on subscription-events / subscription-freeze-events (see listeners).
                // TTL is a safety net if a Kafka message is missed — not the primary freshness path.
                new org.springframework.cache.caffeine.CaffeineCache("admin-users",
                        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(200).build()),
                new org.springframework.cache.caffeine.CaffeineCache("user-statistics",
                        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10).build()),
                new org.springframework.cache.caffeine.CaffeineCache("identity_users",
                        Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(5000).build()),
                new org.springframework.cache.caffeine.CaffeineCache("user_summaries",
                        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000).build()),
                new org.springframework.cache.caffeine.CaffeineCache("user_me",
                        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000).build()),
                new org.springframework.cache.caffeine.CaffeineCache("languages",
                        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build()),
                // Dashboard V2 parallel APIs — short TTL; block failures are soft (blockErrors).
                new org.springframework.cache.caffeine.CaffeineCache("dashboard-v2-summary",
                        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build()),
                new org.springframework.cache.caffeine.CaffeineCache("dashboard-v2-customers",
                        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build())
        ));
        return manager;
    }
}
