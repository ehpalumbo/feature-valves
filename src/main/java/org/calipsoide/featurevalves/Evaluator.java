package org.calipsoide.featurevalves;

import java.util.Optional;

/**
 * Computes a deterministic {@link ExpositionLevel} for a {@link FeatureCheck}.
 * <p>
 * The level decides whether a matching {@link FeatureValve} allows the request.
 * Returning {@link Optional#empty()} signals that the level cannot be determined,
 * which causes the feature to evaluate to {@code false}.
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
