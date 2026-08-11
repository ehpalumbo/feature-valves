package org.calipsoide.featurevalves;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class FeatureTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    private final FeatureValve lowCardinality =
            new FeatureValve("low", ExpositionLevel.ofPercentage(100),
                    Collections.singletonList(new Tag("animal", "cat")));
    private final FeatureValve highCardinality =
            new FeatureValve("high", ExpositionLevel.ofPercentage(50),
                    Arrays.asList(new Tag("animal", "cat"), new Tag("size", "large")));

    private final FeatureCheck matchingCheck = new FeatureCheck(
            Arrays.asList(new Tag("animal", "cat"), new Tag("size", "large"), new Tag("name", "x")));

    private Feature feature(Evaluator evaluator, boolean active) {
        return new Feature(id, Arrays.asList(lowCardinality, highCardinality), evaluator, active);
    }

    private Evaluator fixedLevel(int level) {
        return check -> Optional.of(ExpositionLevel.ofPercentage(level));
    }

    @Test
    public void inactiveFeatureReturnsFalse() {
        final Feature feature = feature(fixedLevel(0), false);
        assertThat(feature.execute(matchingCheck)).isFalse();
    }

    @Test
    public void highestCardinalityMatchingValveGovernsTheAllowDecision() {
        final Feature feature = feature(fixedLevel(75), true);
        assertThat(feature.execute(matchingCheck)).isFalse();
    }

    @Test
    public void highestCardinalityValveAllowsWhenExpositionIsHighEnough() {
        final Feature feature = feature(fixedLevel(25), true);
        assertThat(feature.execute(matchingCheck)).isTrue();
    }

    @Test
    public void noMatchingValveReturnsFalse() {
        final Feature feature = feature(fixedLevel(0), true);
        final FeatureCheck otherTags = new FeatureCheck(Collections.singletonList(new Tag("other", "y")));
        assertThat(feature.execute(otherTags)).isFalse();
    }

    @Test
    public void emptyEvaluatorResultReturnsFalse() {
        final Feature feature = feature(check -> Optional.empty(), true);
        assertThat(feature.execute(matchingCheck)).isFalse();
    }

}
