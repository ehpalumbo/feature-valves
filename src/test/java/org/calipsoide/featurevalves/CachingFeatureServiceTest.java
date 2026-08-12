package org.calipsoide.featurevalves;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * Verifies {@link CachingFeatureService}: accepted features round-trip through
 * the cache, unknown ids yield an empty {@code Mono}, and entries expire after
 * the configured TTL.
 */
public class CachingFeatureServiceTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    private Cache cache(Duration ttl) {
        return new CaffeineCache("features",
                Caffeine.newBuilder().expireAfterWrite(ttl).build());
    }

    private Feature feature() {
        final Evaluator evaluator = check -> Optional.of(ExpositionLevel.ZERO);
        return new Feature(id, Collections.emptyList(), evaluator, true);
    }

    @Test
    public void acceptedFeatureRoundTrips() {
        final var service = new CachingFeatureService(cache(Duration.ofHours(1)));
        final Feature feature = feature();
        service.accept(feature);
        StepVerifier.create(service.findBy(id)).expectNext(feature).verifyComplete();
    }

    @Test
    public void unknownIdYieldsEmpty() {
        final var service = new CachingFeatureService(cache(Duration.ofHours(1)));
        final var unknown = new FeatureId(ClientApplicationId.of("app"), "nope");
        StepVerifier.create(service.findBy(unknown)).verifyComplete();
    }

    @Test
    public void entryExpiresAfterTtl() throws Exception {
        final var service = new CachingFeatureService(cache(Duration.ofSeconds(1)));
        service.accept(feature());
        Thread.sleep(1200);
        StepVerifier.create(service.findBy(id)).verifyComplete();
    }

}
