package org.calipsoide.featurevalves.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * Verifies {@link FeatureCheckResponse} serialization for both {@code true}
 * and {@code false} results.
 */
public class FeatureCheckResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void serializesTrue() throws Exception {
        assertThat(mapper.writeValueAsString(new FeatureCheckResponse(true))).isEqualTo("{\"result\":true}");
    }

    @Test
    public void serializesFalse() throws Exception {
        assertThat(mapper.writeValueAsString(new FeatureCheckResponse(false))).isEqualTo("{\"result\":false}");
    }

}
