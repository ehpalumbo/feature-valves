package org.calipsoide.featurevalves;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link ExpositionLevel}: boundary acceptance and rejection, the
 * {@code ZERO} constant, {@code compareTo} ordering, and string form.
 */
public class ExpositionLevelTest {

    @Test
    public void zeroAndHundredAreValid() {
        assertThat(ExpositionLevel.ofPercentage(0)).isNotNull();
        assertThat(ExpositionLevel.ofPercentage(100)).isNotNull();
    }

    @Test
    public void negativeAndOverHundredThrow() {
        assertThatThrownBy(() -> ExpositionLevel.ofPercentage(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpositionLevel.ofPercentage(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void zeroIsEqualToOfPercentageZero() {
        final ExpositionLevel zero = ExpositionLevel.ofPercentage(0);
        assertThat(ExpositionLevel.ZERO).isEqualTo(zero);
        assertThat(ExpositionLevel.ZERO.hashCode()).isEqualTo(zero.hashCode());
    }

    @Test
    public void compareToOrdersLevels() {
        assertThat(ExpositionLevel.ofPercentage(0).compareTo(ExpositionLevel.ofPercentage(50))).isLessThan(0);
        assertThat(ExpositionLevel.ofPercentage(50).compareTo(ExpositionLevel.ofPercentage(100))).isLessThan(0);
        assertThat(ExpositionLevel.ofPercentage(100).compareTo(ExpositionLevel.ofPercentage(0))).isGreaterThan(0);
    }

    @Test
    public void toStringReturnsNumericPercentage() {
        assertThat(ExpositionLevel.ofPercentage(0).toString()).isEqualTo("0");
        assertThat(ExpositionLevel.ofPercentage(100).toString()).isEqualTo("100");
    }

}
