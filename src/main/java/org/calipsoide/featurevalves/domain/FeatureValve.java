package org.calipsoide.featurevalves.domain;

import java.util.List;

/**
 * A named rollout rule: a set of required {@link Tag}s and an
 * {@link ExpositionLevel} threshold.
 * <p>
 * A valve <em>matches</em> a {@link FeatureCheck} when every required tag is
 * present among the check's tags and the check is not empty (the required
 * tags form a subset of the check's tags), and
 * <em>allows</em> a request when the valve's exposition is strictly greater
 * than the request's level. See {@link Feature#execute(FeatureCheck)}.
 *
 * @param name      the valve name (e.g. {@code all.large.cats})
 * @param exposition the exposure threshold as a percentage
 * @param tags      the required tags, copied defensively
 */
public record FeatureValve(String name, ExpositionLevel exposition, List<Tag> tags) {

    /**
     * Copies the required tags defensively during construction.
     */
    public FeatureValve {
        tags = List.copyOf(tags);
    }

    /**
     * @return the number of required tags; used to decide which matching valve
     *         is the most specific
     */
    int getCardinality() {
        return tags.size();
    }

    /**
     * @param check the request data
     * @return {@code true} when every required tag is present among the check's
     *         tags and the check is not empty
     */
    boolean matches(FeatureCheck check) {
        final List<Tag> present = check.tags();
        return !present.isEmpty() && tags.stream().allMatch(present::contains);
    }

    /**
     * @param level the request's exposure level
     * @return {@code true} when this valve's exposition is strictly greater
     *         than the given level (the request falls strictly below the threshold)
     */
    boolean allows(ExpositionLevel level) {
        return exposition.compareTo(level) > 0;
    }

}
