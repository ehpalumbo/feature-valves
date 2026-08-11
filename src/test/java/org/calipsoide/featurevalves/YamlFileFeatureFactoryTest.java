package org.calipsoide.featurevalves;

import org.junit.jupiter.api.Test;

import java.nio.CharBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class YamlFileFeatureFactoryTest {

    private static final String README_STYLE_YAML =
            "active: true\n" +
                    "eval:\n" +
                    "  - name\n" +
                    "valves:\n" +
                    "  - name: all.large.cats\n" +
                    "    tags:\n" +
                    "      size: large\n" +
                    "      animal: cat\n" +
                    "    value: 10\n" +
                    "  - name: some.small.dogs\n" +
                    "    tags:\n" +
                    "      size: small\n" +
                    "      animal: dog\n" +
                    "    value: 25\n";

    private final YamlFileFeatureFactory factory = new YamlFileFeatureFactory();

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    private Feature read(String yaml) {
        return factory.read(new FeatureFile(id, CharBuffer.wrap(yaml))).block();
    }

    @Test
    public void readsReadmeStyleYaml() {
        final Feature feature = read(README_STYLE_YAML);
        assertThat(feature.getId()).isEqualTo(id);
        assertThat(feature.toString())
                .contains("active=true")
                .contains("evaluator=HashingEvaluator{tags=[name]}")
                .contains("valves=[FeatureValve{name=all.large.cats")
                .contains("exposition=10")
                .contains("name=some.small.dogs")
                .contains("exposition=25");
    }

    @Test
    public void missingActiveDefaultsToTrue() {
        assertThat(read("eval:\n  - name\n").toString()).contains("active=true");
    }

    @Test
    public void missingValvesDefaultsToEmptyList() {
        assertThat(read("active: true\neval:\n  - name\n").toString()).contains("valves=[]");
    }

    @Test
    public void malformedYamlThrowsOnRead() {
        final FeatureFile malformed = new FeatureFile(id, CharBuffer.wrap("active: [unclosed"));
        assertThatThrownBy(() -> factory.read(malformed)).isInstanceOf(Exception.class);
    }

}
