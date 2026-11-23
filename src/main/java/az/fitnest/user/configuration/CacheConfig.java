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
                // Short TTL: freeze/unfreeze (order-backend) does not evict this cache, so a long
                // window left "Dondurulmuş Status" stuck at a stale 0 after admin freezes someone.
                new org.springframework.cache.caffeine.CaffeineCache("admin-users",
                        Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(200).build()),
                new org.springframework.cache.caffeine.CaffeineCache("user-statistics",
                        Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(10).build()),
                new org.springframework.cache.caffeine.CaffeineCache("identity_users",
                        Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(5000).build()),
                new org.springframework.cache.caffeine.CaffeineCache("user_summaries",
                        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000).build()),
                new org.springframework.cache.caffeine.CaffeineCache("user_me",
                        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000).build()),
                new org.springframework.cache.caffeine.CaffeineCache("languages",
                        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build())
        ));
        return manager;
    }
}
