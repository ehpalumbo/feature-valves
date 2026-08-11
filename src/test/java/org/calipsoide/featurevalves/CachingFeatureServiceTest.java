package org.calipsoide.featurevalves;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.Optional;

public class CachingFeatureServiceTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    private Feature feature() {
        final Evaluator evaluator = check -> Optional.of(ExpositionLevel.ZERO);
        return new Feature(id, Collections.emptyList(), evaluator, true);
    }

    @Test
    public void acceptedFeatureRoundTrips() {
        final CachingFeatureService service = new CachingFeatureService("PT1H");
        final Feature feature = feature();
        service.accept(feature);
        StepVerifier.create(service.findBy(id)).expectNext(feature).verifyComplete();
    }

    @Test
    public void unknownIdYieldsEmpty() {
        final CachingFeatureService service = new CachingFeatureService("PT1H");
        final FeatureId unknown = new FeatureId(ClientApplicationId.of("app"), "nope");
        StepVerifier.create(service.findBy(unknown)).verifyComplete();
    }

    @Test
    public void entryExpiresAfterTtl() throws Exception {
        final CachingFeatureService service = new CachingFeatureService("PT1S");
        service.accept(feature());
        Thread.sleep(1200);
        StepVerifier.create(service.findBy(id)).verifyComplete();
    }

}
