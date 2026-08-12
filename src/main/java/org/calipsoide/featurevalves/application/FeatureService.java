package org.calipsoide.featurevalves.application;

import org.calipsoide.featurevalves.domain.Feature;
import org.calipsoide.featurevalves.domain.FeatureId;

import reactor.core.publisher.Mono;

/**
 * Read-side contract for resolving a {@link Feature} by its {@link FeatureId}.
 *
 * @see CachingFeatureService
 */
public interface FeatureService {

    /**
     * Returns the feature matching the given identifier.
     *
     * @param id the feature identifier
     * @return a {@code Mono} of the {@link Feature} that completes empty when
     *         no such feature is known
     */
    Mono<Feature> findBy(FeatureId id);

}
