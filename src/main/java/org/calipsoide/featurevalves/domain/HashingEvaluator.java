package org.calipsoide.featurevalves.domain;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;

/**
 * An {@link Evaluator} that derives the exposure level from the hash of request
 * tag values.
 * <p>
 * The values of the tags listed in {@code tagNames} are concatenated in request
 * order with {@code ':'} separators and hashed via {@link String#hashCode()};
 * the level is {@code Math.abs(hash) % 100}. The same request data therefore
 * always maps to the same level (deterministic and sticky). When none of the
 * configured tags are present, no level is produced.
 *
 * @param tagNames the request tag codes whose values participate in the hash
 */
public record HashingEvaluator(List<String> tagNames) implements Evaluator {

    /**
     * {@inheritDoc}
     * <p>
     * If any tag in {@code tagNames} is missing from the check, it is simply
     * skipped when building the hash source.
     *
     * @param check the request data to evaluate
     * @return a level in {@code [0, 99]}, or {@link Optional#empty()} when none
     *         of the configured tags are supplied
     */
    @Override
    public Optional<ExpositionLevel> evaluate(FeatureCheck check) {
        final List<String> values = check.tags().stream()
                .filter(tag -> tagNames.contains(tag.code()))
                .map(Tag::value)
                .collect(toList());
        if (values.isEmpty()) {
            return Optional.empty();
        } else {
            final String source = String.join(":", values);
            final int level = Math.abs(source.hashCode() % 100);
            return Optional.of(ExpositionLevel.ofPercentage(level));
        }
    }

}