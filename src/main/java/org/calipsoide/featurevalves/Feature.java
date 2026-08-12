package org.calipsoide.featurevalves;

import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/**
 * A feature flag definition that decides, per request, whether the flag is ON
 * or OFF.
 * <p>
 * A feature combines the ordered list of {@link FeatureValve}s that may match
 * a {@link FeatureCheck} with the {@link Evaluator} that turns a check into an
 * {@link ExpositionLevel}. See {@link #execute(FeatureCheck)} for the
 * evaluation semantics.
 *
 * @see FeatureValve
 * @see Evaluator
 * @see FeatureId
 */
@Getter
@ToString
public class Feature {

    private final FeatureId id;

    private final List<FeatureValve> valves;

    private final Evaluator evaluator;

    private final boolean active;

    /**
     * Creates a feature.
     *
     * @param id        the feature identifier
     * @param valves    the valves to consider, copied defensively
     * @param evaluator the evaluator computing the request's exposure level
     * @param active    whether the feature participates in evaluation at all
     */
    public Feature(FeatureId id, List<FeatureValve> valves, Evaluator evaluator, boolean active) {
        this.id = id;
        this.valves = List.copyOf(valves);
        this.evaluator = evaluator;
        this.active = active;
    }

    /**
     * Evaluates a feature check to a boolean result.
     * <p>
     * Returns {@code false} immediately when the feature is inactive. Otherwise
     * the request is allowed only when the {@link FeatureValve} matching the
     * check with the most required tags (highest cardinality) allows the
     * computed exposure level. No matching valve, or an undeterminable level,
     * also yields {@code false}.
     *
     * @param check the request data to evaluate
     * @return {@code true} if the feature flag is ON for this check, else {@code false}
     */
    public boolean execute(FeatureCheck check) {
        if (active) {
            return valves.stream()
                    .filter(valve -> valve.matches(check))
                    .max((one, two) -> {
                        final int first = one.getCardinality();
                        final int second = two.getCardinality();
                        return Integer.valueOf(first).compareTo(second);
                    })
                    .flatMap(valve -> evaluator.evaluate(check).map(valve::allows))
                    .orElse(false);
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Feature feature = (Feature) o;
        return Objects.equals(id, feature.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
