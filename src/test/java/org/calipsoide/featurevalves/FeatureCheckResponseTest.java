package org.calipsoide.featurevalves;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

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
