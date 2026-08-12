package org.calipsoide.featurevalves.domain;

/**
 * Uniquely identifies a feature within a client application.
 * <p>
 * The feature code is normalized to lower case so that lookups are
 * case-insensitive. String form is {@code <application>:<feature>}.
 *
 * @param applicationId the owning client application
 * @param featureCode   the feature code, normalized to lower case
 * @see ClientApplicationId
 * @see Feature
 */
public record FeatureId(ClientApplicationId applicationId, String featureCode) {

    /**
     * Normalizes the feature code to lower case during construction.
     */
    public FeatureId {
        featureCode = featureCode.toLowerCase();
    }

    @Override
    public String toString() {
        return applicationId + ":" + featureCode;
    }

}
