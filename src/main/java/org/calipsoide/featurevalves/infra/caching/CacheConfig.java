package org.calipsoide.featurevalves.infra.caching;

import java.time.Duration;
import java.util.concurrent.ConcurrentMap;

import org.calipsoide.featurevalves.domain.Feature;
import org.calipsoide.featurevalves.domain.FeatureId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Spring configuration declaring the Caffeine-backed in-memory store that holds
 * parsed {@link Feature}s.
 * <p>
 * The live {@link ConcurrentMap} view of a Caffeine cache is exposed directly,
 * keeping Caffeine confined to this infrastructure package while the
 * application layer works with a plain {@code ConcurrentMap} and preserves the
 * configured time-to-live for entries that are no longer refreshed.
 *
 * @see org.calipsoide.featurevalves.application.InMemoryFeatureService
 */
@Configuration
public class CacheConfig {

    /**
     * Exposes a live map view of a Caffeine cache with the configured
     * time-to-live.
     *
     * @param ttl the entry TTL from {@code features.cache.ttl}
     * @return the map of cached features, keyed by {@link FeatureId}
     */
    @Bean
    public ConcurrentMap<FeatureId, Feature> featureMap(@Value("${features.cache.ttl}") Duration ttl) {
        final Cache<FeatureId, Feature> cache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
        return cache.asMap();
    }

}
