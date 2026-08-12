package org.calipsoide.featurevalves;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Verifies {@link FeatureCheckRequest} deserialization: the flat JSON request
 * body binds directly to the tags map.
 */
public class FeatureCheckRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void deserializesFlatTagsMap() throws Exception {
        final FeatureCheckRequest request =
                mapper.readValue("{\"a\":\"1\",\"b\":\"2\"}", FeatureCheckRequest.class);
        final Map<String, String> tags = request.tags();
        assertThat(tags).containsExactly(entry("a", "1"), entry("b", "2"));
    }

    @Test
    public void tagsAreBoundFromTheRootObject() throws Exception {
        final FeatureCheckRequest request =
                mapper.readValue("{\"name\":\"x\",\"t\":\"x\"}", FeatureCheckRequest.class);
        assertThat(request.tags()).containsOnly(entry("name", "x"), entry("t", "x"));
    }

}
