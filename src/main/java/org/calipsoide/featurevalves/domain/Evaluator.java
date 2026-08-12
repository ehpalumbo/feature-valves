package org.calipsoide.featurevalves.domain;

import java.util.Optional;

/**
 * Computes a deterministic {@link ExpositionLevel} for a {@link FeatureCheck}.
 * <p>
 * The level decides whether a matching {@link FeatureValve} allows the request.
 * Returning {@link Optional#empty()} signals that the level cannot be
 * determined.
 *
 * @see HashingEvaluator
 */
public interface Evaluator {

    /**
     * Evaluates a feature check to an exposure level.
     *
     * @param check the request data to evaluate
     * @return the computed exposure level, or {@link Optional#empty()} when no
     *         level can be determined from the check
     */
    Optional<ExpositionLevel> evaluate(FeatureCheck check);

}
