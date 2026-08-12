package org.calipsoide.featurevalves;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration declaring the Caffeine-backed cache that holds parsed
 * {@link Feature}s.
 *
 * @see CachingFeatureService
 */
@Configuration
public class CacheConfig {

    /**
     * Creates the configuration (no explicit state).
     */
    public CacheConfig() {
    }

    /**
     * Defines the {@code features} cache with the configured time-to-live.
     *
     * @param ttl the cache TTL from {@code features.cache.ttl}
     * @return the configured {@link CaffeineCache}
     */
    @Bean
    public CaffeineCache featuresCache(@Value("${features.cache.ttl}") Duration ttl) {
        return new CaffeineCache("features",
                Caffeine.newBuilder().expireAfterWrite(ttl).build());
    }

}
