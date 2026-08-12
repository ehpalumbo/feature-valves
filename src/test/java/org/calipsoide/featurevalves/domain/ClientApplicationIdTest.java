package org.calipsoide.featurevalves.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ClientApplicationId}: the {@code of} factory normalizes to
 * lower case and equality/hashing are case-insensitive.
 */
public class ClientApplicationIdTest {

    @Test
    public void ofNormalizesToLowerCase() {
        assertThat(ClientApplicationId.of("Foo").toString()).isEqualTo("foo");
    }

    @Test
    public void equalsAndHashCodeAreCaseInsensitive() {
        final ClientApplicationId foo = ClientApplicationId.of("Foo");
        final ClientApplicationId upper = ClientApplicationId.of("FOO");
        assertThat(foo).isEqualTo(upper);
        assertThat(foo.hashCode()).isEqualTo(upper.hashCode());
        assertThat(ClientApplicationId.of("bar")).isNotEqualTo(foo);
    }

}
