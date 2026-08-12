package org.calipsoide.featurevalves;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static reactor.core.publisher.Mono.just;

/**
 * Converts a {@link FeatureFile} into a domain {@link Feature} by parsing its
 * YAML content via SnakeYAML.
 * <p>
 * Missing {@code eval} and {@code valves} sections default to empty, and a
 * missing {@code active} field defaults to {@code true}.
 *
 * @see FeatureLoader
 */
@Service
public class YamlFileFeatureFactory {

    /**
     * Creates the factory (stateless).
     */
    public YamlFileFeatureFactory() {
    }

    /**
     * Parses the given feature file into a {@link Feature}.
     *
     * @param file the raw feature file to parse
     * @return a {@code Mono} of the resulting {@link Feature}
     */
    public Mono<Feature> read(FeatureFile file) {
        final String content = file.buffer().toString();
        final FeatureData data = new Yaml().loadAs(content, FeatureData.class);
        final HashingEvaluator evaluator =
                new HashingEvaluator(Optional.ofNullable(data.eval).orElseGet(List::of));
        final List<FeatureValve> valves =
                Optional.ofNullable(data.valves)
                        .orElseGet(List::of)
                        .stream()
                        .map(valve -> {
                            final List<Tag> tags =
                                    Optional.ofNullable(valve.tags)
                                            .orElseGet(Map::of)
                                            .entrySet()
                                            .stream()
                                            .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                                            .collect(toList());
                            final ExpositionLevel exposition =
                                    Optional.ofNullable(valve.value)
                                            .map(ExpositionLevel::ofPercentage)
                                            .orElse(ExpositionLevel.ZERO);
                            return new FeatureValve(valve.name, exposition, tags);
                        })
                        .collect(toList());
        return just(new Feature(file.id(), valves, evaluator, data.active));
    }

    private static class FeatureData {

        public boolean active = true;
        public List<String> eval;
        public List<ValveData> valves;

        private static class ValveData {

            public String name;
            public Map<String, String> tags;
            public Integer value;

        }

    }
}
