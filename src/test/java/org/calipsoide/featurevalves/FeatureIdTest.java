package org.calipsoide.featurevalves;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FeatureIdTest {

    private final ClientApplicationId app = ClientApplicationId.of("app");

    @Test
    public void lowercasesTheFeatureCode() {
        assertThat(new FeatureId(app, "MY-FEATURE").getFeatureCode()).isEqualTo("my-feature");
    }

    @Test
    public void equalsAndHashCodeOnBothFields() {
        final FeatureId first = new FeatureId(app, "my-feature");
        final FeatureId second = new FeatureId(app, "my-feature");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new FeatureId(app, "other")).isNotEqualTo(first);
        assertThat(new FeatureId(ClientApplicationId.of("other-app"), "my-feature")).isNotEqualTo(first);
    }

    @Test
    public void tagEqualsAndHashCodeMatchOnContent() {
        final Tag first = new Tag("animal", "cat");
        final Tag second = new Tag("animal", "cat");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new Tag("animal", "dog")).isNotEqualTo(first);
        assertThat(new Tag("size", "cat")).isNotEqualTo(first);
    }

    @Test
    public void featureCheckEqualsAndHashCodeMatchOnContent() {
        final FeatureCheck first = new FeatureCheck(Collections.singletonList(new Tag("animal", "cat")));
        final FeatureCheck second = new FeatureCheck(Collections.singletonList(new Tag("animal", "cat")));
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new FeatureCheck(Collections.singletonList(new Tag("animal", "dog")))).isNotEqualTo(first);
        assertThat(new FeatureCheck(Arrays.asList(new Tag("animal", "cat"), new Tag("size", "large"))))
                .isNotEqualTo(first);
    }

}
