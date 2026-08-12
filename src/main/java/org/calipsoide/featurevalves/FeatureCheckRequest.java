package org.calipsoide.featurevalves;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The request payload for a feature check: a flat map of request tags. The
 * JSON request body is bound directly to the tag map.
 *
 * @param tags the request tags as key-value pairs
 * @see FeatureCheckController#check(String, String, FeatureCheckRequest)
 */
public record FeatureCheckRequest(Map<String, String> tags) {

    /**
     * Delegating creator that binds the JSON request body directly to the tags
     * map.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public FeatureCheckRequest {
    }

}
