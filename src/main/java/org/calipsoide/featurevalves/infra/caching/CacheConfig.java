package org.calipsoide.featurevalves.infra.caching;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Spring configuration declaring the Caffeine-backed cache that holds parsed
 * {@link org.calipsoide.featurevalves.domain.Feature}s.
 *
 * @see org.calipsoide.featurevalves.application.CachingFeatureService
 */
@Configuration
public class CacheConfig {

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
