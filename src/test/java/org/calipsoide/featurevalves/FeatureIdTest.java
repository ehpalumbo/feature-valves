package org.calipsoide.featurevalves;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * Verifies the value semantics of {@link FeatureId}, {@link Tag}, and
 * {@link FeatureCheck}: lower-casing normalization and content-based
 * equality/hashing.
 */
public class FeatureIdTest {

    private final ClientApplicationId app = ClientApplicationId.of("app");

    @Test
    public void lowercasesTheFeatureCode() {
        assertThat(new FeatureId(app, "MY-FEATURE").featureCode()).isEqualTo("my-feature");
    }

    @Test
    public void equalsAndHashCodeOnBothFields() {
        final var first = new FeatureId(app, "my-feature");
        final var second = new FeatureId(app, "my-feature");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new FeatureId(app, "other")).isNotEqualTo(first);
        assertThat(new FeatureId(ClientApplicationId.of("other-app"), "my-feature")).isNotEqualTo(first);
    }

    @Test
    public void tagEqualsAndHashCodeMatchOnContent() {
        final var first = new Tag("animal", "cat");
        final var second = new Tag("animal", "cat");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new Tag("animal", "dog")).isNotEqualTo(first);
        assertThat(new Tag("size", "cat")).isNotEqualTo(first);
    }

    @Test
    public void featureCheckEqualsAndHashCodeMatchOnContent() {
        final var first = new FeatureCheck(Collections.singletonList(new Tag("animal", "cat")));
        final var second = new FeatureCheck(Collections.singletonList(new Tag("animal", "cat")));
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new FeatureCheck(Collections.singletonList(new Tag("animal", "dog")))).isNotEqualTo(first);
        assertThat(new FeatureCheck(Arrays.asList(new Tag("animal", "cat"), new Tag("size", "large"))))
                .isNotEqualTo(first);
    }

}
