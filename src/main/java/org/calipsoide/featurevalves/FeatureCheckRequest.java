package org.calipsoide.featurevalves;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Objects;

import java.util.Map;

/**
 * Created by epalumbo on 9/16/17.
 */
public class FeatureCheckRequest {

    private Map<String, String> tags;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public FeatureCheckRequest(Map<String, String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeatureCheckRequest that = (FeatureCheckRequest) o;
        return Objects.equal(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tags);
    }

}
