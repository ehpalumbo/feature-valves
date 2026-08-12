package org.calipsoide.featurevalves;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

/**
 * Created by epalumbo on 9/16/17.
 */
public record FeatureCheckRequest(Map<String, String> tags) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public FeatureCheckRequest {
    }

}