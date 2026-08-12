package org.calipsoide.featurevalves;

/**
 * The response payload for a feature check: whether the feature flag is ON.
 *
 * @param result {@code true} when the feature is ON for the request
 * @see FeatureCheckController#check(String, String, FeatureCheckRequest)
 */
public record FeatureCheckResponse(boolean result) {

}
