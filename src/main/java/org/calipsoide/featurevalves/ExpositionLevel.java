package org.calipsoide.featurevalves;

import org.springframework.util.Assert;

import java.util.Objects;

/**
 * A rollout exposure threshold expressed as an integer percentage in
 * the closed range {@code [0, 100]}.
 * <p>
 * A {@link FeatureValve} allows a request when its own exposition is strictly
 * greater than the request's {@link ExpositionLevel}, so raising a valve's
 * percentage widens exposure. See {@link FeatureValve#allows(ExpositionLevel)}.
 */
public class ExpositionLevel implements Comparable<ExpositionLevel> {

    /** Zero-percent exposure: allows no requests. */
    public static final ExpositionLevel ZERO = ofPercentage(0);

    private final Integer percentage;

    private ExpositionLevel(int percentage) {
        this.percentage = percentage;
    }

    /**
     * Creates an exposure level from a percentage.
     *
     * @param percentage a value between 0 and 100 inclusive
     * @return the matching {@code ExpositionLevel}
     * @throws IllegalArgumentException if {@code percentage} is outside {@code [0, 100]}
     */
    static ExpositionLevel ofPercentage(int percentage) {
        Assert.isTrue(percentage >= 0 && percentage <= 100, "percentage must be between 0 and 100");
        return new ExpositionLevel(percentage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpositionLevel that = (ExpositionLevel) o;
        return percentage == that.percentage;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(percentage);
    }

    @Override
    public String toString() {
        return Integer.toString(percentage);
    }

    @Override
    public int compareTo(ExpositionLevel other) {
        return percentage.compareTo(other.percentage);
    }

}
