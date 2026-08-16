package org.calipsoide.featurevalves.application;

import static reactor.core.publisher.Mono.justOrEmpty;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.calipsoide.featurevalves.domain.Feature;
import org.calipsoide.featurevalves.domain.FeatureId;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * A {@link FeatureService} backed by an in-memory {@link ConcurrentMap} that
 * also acts as the {@link Subscriber} fed by the load pipeline.
 * <p>
 * The request path only ever reads from this map; parsed {@link Feature}s are
 * written back here by {@link FeatureLoader} after each refresh. Each
 * subscription (one per refresh tick) tracks the ids seen during that tick and
 * evicts, on completion, the entries that are no longer present — so features
 * removed from the repository stop being served on the next refresh.
 *
 * @see FeatureLoader
 * @see org.calipsoide.featurevalves.infra.caching.CacheConfig
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InMemoryFeatureService implements FeatureService, Subscriber<Feature> {

    private final ConcurrentMap<FeatureId, Feature> features;

    private final Set<FeatureId> seen = new HashSet<>();

    /**
     * {@inheritDoc}
     *
     * @param id the feature identifier
     * @return the cached {@link Feature}, or an empty {@code Mono} on a miss
     */
    @Override
    public Mono<Feature> findBy(FeatureId id) {
        return justOrEmpty(features.get(id));
    }

    /**
     * Starts a new refresh tick by resetting the per-tick seen set.
     *
     * @param subscription the subscription to the refresh stream
     */
    @Override
    public void onSubscribe(Subscription subscription) {
        seen.clear();
        subscription.request(Long.MAX_VALUE);
    }

    /**
     * Stores a freshly loaded feature and records it as seen in this tick.
     *
     * @param feature the parsed feature to cache
     */
    @Override
    public void onNext(Feature feature) {
        final Feature previous = features.put(feature.getId(), feature);
        if (previous != null) {
            log.debug("Reloaded {}", feature);
        } else {
            log.debug("Loaded {}", feature);
        }
        seen.add(feature.getId());
    }

    /**
     * Logs a failed refresh tick and leaves the cache untouched.
     * <p>
     * The entry TTL remains the backstop so a transient source failure does not
     * wipe the cache.
     *
     * @param throwable the failure that aborted the refresh
     */
    @Override
    public void onError(Throwable throwable) {
        log.error("Feature refresh failed; cache unchanged.", throwable);
    }

    /**
     * Evicts every cached feature that was not seen in this completed tick.
     */
    @Override
    public void onComplete() {
        features.keySet().removeIf(id -> !seen.contains(id));
        seen.clear();
        log.debug("Refresh complete; {} features available.", features.size());
    }

}
