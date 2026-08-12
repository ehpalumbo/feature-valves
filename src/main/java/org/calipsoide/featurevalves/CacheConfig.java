package org.calipsoide.featurevalves;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public CaffeineCache featuresCache(@Value("${features.cache.ttl}") Duration ttl) {
        return new CaffeineCache("features",
                Caffeine.newBuilder().expireAfterWrite(ttl).build());
    }

}
