package org.calipsoide.featurevalves;

import java.util.List;

/**
 * Created by epalumbo on 9/16/17.
 */
public record FeatureValve(String name, ExpositionLevel exposition, List<Tag> tags) {

    public FeatureValve {
        tags = List.copyOf(tags);
    }

    int getCardinality() {
        return tags.size();
    }

    boolean matches(FeatureCheck check) {
        final List<Tag> present = check.tags();
        return !present.isEmpty() && tags.stream().allMatch(present::contains);
    }

    boolean allows(ExpositionLevel level) {
        return exposition.compareTo(level) > 0;
    }

}