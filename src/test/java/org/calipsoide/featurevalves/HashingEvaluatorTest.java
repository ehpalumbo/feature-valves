package org.calipsoide.featurevalves;

import com.google.common.base.Joiner;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class HashingEvaluatorTest {

    private final HashingEvaluator evaluator = new HashingEvaluator(Collections.singletonList("name"));

    private FeatureCheck checkWith(Tag... tags) {
        return new FeatureCheck(Arrays.asList(tags));
    }

    @Test
    public void evaluateIsDeterministicWithinRange() {
        final FeatureCheck check = checkWith(new Tag("name", "little.rose"));
        final ExpositionLevel first = evaluator.evaluate(check).get();
        final ExpositionLevel second = evaluator.evaluate(check).get();
        assertThat(first).isEqualTo(second);
        final int level = Integer.parseInt(first.toString());
        assertThat(level).isBetween(0, 99);
    }

    @Test
    public void onlyConfiguredTagsAreHashed() {
        final FeatureCheck withName = checkWith(new Tag("name", "x"));
        final FeatureCheck withExtraTag = checkWith(new Tag("name", "x"), new Tag("age", "3"));
        assertThat(evaluator.evaluate(withExtraTag)).isEqualTo(evaluator.evaluate(withName));
    }

    @Test
    public void noConfiguredTagsYieldsEmpty() {
        final FeatureCheck check = checkWith(new Tag("age", "3"));
        assertThat(evaluator.evaluate(check)).isEmpty();
    }

    @Test
    public void levelMatchesExpectedHashFormula() {
        final String source = Joiner.on(":").join(Collections.singletonList("little.rose"));
        final int expected = Math.abs(source.hashCode()) % 100;
        final FeatureCheck check = checkWith(new Tag("name", "little.rose"));
        assertThat(evaluator.evaluate(check).get()).isEqualTo(ExpositionLevel.ofPercentage(expected));
    }

}
