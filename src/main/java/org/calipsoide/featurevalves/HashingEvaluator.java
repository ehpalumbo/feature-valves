package org.calipsoide.featurevalves;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Created by epalumbo on 9/16/17.
 */
public record HashingEvaluator(List<String> tagNames) implements Evaluator {

    @Override
    public Optional<ExpositionLevel> evaluate(FeatureCheck check) {
        final List<String> values =
                check.tags().stream()
                        .filter(tag -> tagNames.contains(tag.code()))
                        .map(Tag::value)
                        .collect(toList());
        if (values.isEmpty()) {
            return Optional.empty();
        } else {
            final String source = String.join(":", values);
            final int hash = Math.abs(source.hashCode());
            return Optional.of(ExpositionLevel.ofPercentage(hash % 100));
        }
    }

}