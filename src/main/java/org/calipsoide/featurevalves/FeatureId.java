package org.calipsoide.featurevalves;

/**
 * Created by epalumbo on 9/17/17.
 */
public record FeatureId(ClientApplicationId applicationId, String featureCode) {

    public FeatureId {
        featureCode = featureCode.toLowerCase();
    }

    @Override
    public String toString() {
        return applicationId + ":" + featureCode;
    }

}
