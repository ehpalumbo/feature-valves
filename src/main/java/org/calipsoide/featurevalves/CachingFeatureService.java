package org.calipsoide.featurevalves;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Consumer;

import static reactor.core.publisher.Mono.justOrEmpty;

/**
 * Created by epalumbo on 9/17/17.
 */
@Service
public class CachingFeatureService implements FeatureService, Consumer<Feature> {

    private static final Logger logger = LoggerFactory.getLogger(CachingFeatureService.class);

    private final Cache cache;

    public CachingFeatureService(@Value("${features.cache.ttl}") String ttl) {
        this.cache = new CaffeineCache("features",
                Caffeine.newBuilder().expireAfterWrite(Duration.parse(ttl)).build());
    }

    @Override
    public Mono<Feature> findBy(FeatureId id) {
        final Feature cached = cache.get(id, Feature.class);
        return justOrEmpty(cached);
    }

    @Override
    public void accept(Feature feature) {
        cache.put(feature.getId(), feature);
        logger.debug("Reloaded - {}", feature);
    }

}