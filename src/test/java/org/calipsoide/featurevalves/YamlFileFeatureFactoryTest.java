package org.calipsoide.featurevalves;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.CharBuffer;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link YamlFileFeatureFactory}: README-style YAML is parsed into the
 * expected domain model, missing fields take their defaults, and malformed YAML
 * propagates an error.
 */
public class YamlFileFeatureFactoryTest {

  private static final String README_STYLE_YAML = """
      active: true
      eval:
        - name
      valves:
        - name: all.large.cats
          tags:
            size: large
            animal: cat
          value: 10
        - name: some.small.dogs
          tags:
            size: small
            animal: dog
          value: 25
      """;

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
        .contains("evaluator=HashingEvaluator[tagNames=[name]]")
        .contains("valves=[FeatureValve[name=all.large.cats")
        .contains("exposition=10")
        .contains("name=some.small.dogs")
        .contains("exposition=25");
  }

  @Test
  public void missingActiveDefaultsToTrue() {
    assertThat(read("""
        eval:
          - name
        """).toString()).contains("active=true");
  }

  @Test
  public void missingValvesDefaultsToEmptyList() {
    assertThat(read("""
        active: true
        eval:
          - name
        """).toString()).contains("valves=[]");
  }

  @Test
  public void malformedYamlThrowsOnRead() {
    final var malformed = new FeatureFile(id, CharBuffer.wrap("active: [unclosed"));
    assertThatThrownBy(() -> factory.read(malformed)).isInstanceOf(Exception.class);
  }

}
