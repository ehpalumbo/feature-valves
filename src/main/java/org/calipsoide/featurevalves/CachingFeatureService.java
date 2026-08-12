package org.calipsoide.featurevalves;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static reactor.core.publisher.Mono.justOrEmpty;

/**
 * A {@link FeatureService} backed by an in-memory {@link Cache} that also acts
 * as the {@link Consumer} fed by the load pipeline.
 * <p>
 * The request path only ever reads from this cache; parsed {@link Feature}s are
 * written back here by {@link FeatureLoader} after each refresh.
 *
 * @see FeatureLoader
 * @see CacheConfig
 */
@Service
public class CachingFeatureService implements FeatureService, Consumer<Feature> {

    private static final Logger logger = LoggerFactory.getLogger(CachingFeatureService.class);

    private final Cache cache;

    /**
     * Creates the service over the given cache.
     *
     * @param cache the underlying Spring {@link Cache} holding parsed features
     */
    public CachingFeatureService(Cache cache) {
        this.cache = cache;
    }

    /**
     * {@inheritDoc}
     *
     * @param id the feature identifier
     * @return the cached {@link Feature}, or an empty {@code Mono} on a miss
     */
    @Override
    public Mono<Feature> findBy(FeatureId id) {
        final Feature cached = cache.get(id, Feature.class);
        return justOrEmpty(cached);
    }

    /**
     * Stores a freshly loaded feature in the cache.
     *
     * @param feature the parsed feature to cache
     */
    @Override
    public void accept(Feature feature) {
        cache.put(feature.getId(), feature);
        logger.debug("Reloaded - {}", feature);
    }

}
