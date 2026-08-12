package org.calipsoide.featurevalves;

import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/**
 * Created by epalumbo on 9/16/17.
 */
@Getter
@ToString
public class Feature {

    private final FeatureId id;

    private final List<FeatureValve> valves;

    private final Evaluator evaluator;

    private final boolean active;

    public Feature(FeatureId id, List<FeatureValve> valves, Evaluator evaluator, boolean active) {
        this.id = id;
        this.valves = List.copyOf(valves);
        this.evaluator = evaluator;
        this.active = active;
    }

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
