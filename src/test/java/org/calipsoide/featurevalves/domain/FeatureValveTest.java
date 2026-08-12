package org.calipsoide.featurevalves.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link FeatureValve} semantics: matching requires every valve tag to
 * be present, allowing requires a strictly greater exposition, and cardinality
 * is the required tag count.
 */
public class FeatureValveTest {

    private final FeatureValve valve = new FeatureValve("cats", ExpositionLevel.ofPercentage(50),
            Arrays.asList(new Tag("animal", "cat"), new Tag("size", "large")));

    @Test
    public void matchesOnlyWhenAllValveTagsArePresent() {
        final var allTags = new FeatureCheck(
                Arrays.asList(new Tag("animal", "cat"), new Tag("size", "large"), new Tag("name", "x")));
        assertThat(valve.matches(allTags)).isTrue();
        final var missingTag = new FeatureCheck(Collections.singletonList(new Tag("animal", "cat")));
        assertThat(valve.matches(missingTag)).isFalse();
        final var empty = new FeatureCheck(Collections.emptyList());
        assertThat(valve.matches(empty)).isFalse();
    }

    @Test
    public void allowsOnlyWhenExpositionIsGreaterThanLevel() {
        assertThat(valve.allows(ExpositionLevel.ofPercentage(0))).isTrue();
        assertThat(valve.allows(ExpositionLevel.ofPercentage(49))).isTrue();
        assertThat(valve.allows(ExpositionLevel.ofPercentage(50))).isFalse();
        assertThat(valve.allows(ExpositionLevel.ofPercentage(100))).isFalse();
    }

    @Test
    public void hundredAllowsAnyLevelZeroAllowsNone() {
        final var alwaysOn = new FeatureValve("on", ExpositionLevel.ofPercentage(100), Collections.emptyList());
        assertThat(alwaysOn.allows(ExpositionLevel.ofPercentage(99))).isTrue();
        final var alwaysOff = new FeatureValve("off", ExpositionLevel.ZERO, Collections.emptyList());
        assertThat(alwaysOff.allows(ExpositionLevel.ofPercentage(0))).isFalse();
    }

    @Test
    public void cardinalityIsTheValveTagCount() {
        assertThat(valve.getCardinality()).isEqualTo(2);
        final var noTags = new FeatureValve("none", ExpositionLevel.ZERO, Collections.emptyList());
        assertThat(noTags.getCardinality()).isEqualTo(0);
    }

}
