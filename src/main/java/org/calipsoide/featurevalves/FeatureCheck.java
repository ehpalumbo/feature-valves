package org.calipsoide.featurevalves;

import java.util.List;

/**
 * The request data against which a {@link Feature} is evaluated: a set of
 * key-value {@link Tag}s.
 *
 * @param tags the request tags
 * @see Feature#execute(FeatureCheck)
 */
public record FeatureCheck(List<Tag> tags) {
}
