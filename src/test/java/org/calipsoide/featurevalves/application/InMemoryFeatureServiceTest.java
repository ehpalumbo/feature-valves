package org.calipsoide.featurevalves.application;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.calipsoide.featurevalves.domain.ClientApplicationId;
import org.calipsoide.featurevalves.domain.Evaluator;
import org.calipsoide.featurevalves.domain.ExpositionLevel;
import org.calipsoide.featurevalves.domain.Feature;
import org.calipsoide.featurevalves.domain.FeatureId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import reactor.test.StepVerifier;

/**
 * Verifies {@link InMemoryFeatureService}: features fed to the subscriber
 * round-trip through the map, unknown ids yield an empty {@code Mono}, entries
 * expire after the configured TTL, and a completed refresh tick evicts the
 * features no longer seen while a failed tick leaves the cache untouched.
 */
public class InMemoryFeatureServiceTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    private final FeatureId other = new FeatureId(ClientApplicationId.of("app"), "other");

    private InMemoryFeatureService service = new InMemoryFeatureService(new ConcurrentHashMap<>());

    private Feature feature(FeatureId featureId) {
        final Evaluator evaluator = check -> Optional.of(ExpositionLevel.ZERO);
        return new Feature(featureId, Collections.emptyList(), evaluator, true);
    }

    private void feed(InMemoryFeatureService service, Feature... features) {
        service.onSubscribe(new TestSubscription());
        for (Feature feature : features) {
            service.onNext(feature);
        }
    }

    @BeforeEach
    public void clear() {
        service = new InMemoryFeatureService(new ConcurrentHashMap<>());
    }

    @Test
    public void acceptedFeatureRoundTrips() {
        feed(service, feature(id));
        StepVerifier.create(service.findBy(id)).expectNext(feature(id)).verifyComplete();
    }

    @Test
    public void unknownIdYieldsEmpty() {
        final var unknown = new FeatureId(ClientApplicationId.of("app"), "nope");
        StepVerifier.create(service.findBy(unknown)).verifyComplete();
    }

    @Test
    public void completedTickEvictsFeaturesNotSeen() {
        feed(service, feature(id), feature(other));
        feed(service, feature(id));
        service.onComplete();
        StepVerifier.create(service.findBy(id)).expectNext(feature(id)).verifyComplete();
        StepVerifier.create(service.findBy(other)).verifyComplete();
    }

    @Test
    public void emptyTickEvictsEverything() {
        feed(service, feature(id), feature(other));
        service.onSubscribe(new TestSubscription());
        service.onComplete();
        StepVerifier.create(service.findBy(id)).verifyComplete();
        StepVerifier.create(service.findBy(other)).verifyComplete();
    }

    @Test
    public void failedTickLeavesCacheUntouched() {
        feed(service, feature(id), feature(other));
        service.onSubscribe(new TestSubscription());
        service.onNext(feature(id));
        service.onError(new IllegalStateException("git unavailable"));
        StepVerifier.create(service.findBy(id)).expectNext(feature(id)).verifyComplete();
        StepVerifier.create(service.findBy(other)).expectNext(feature(other)).verifyComplete();
    }

    private static final class TestSubscription implements Subscription {

        @Override
        public void request(long n) {
            // never issued by the service
        }

        @Override
        public void cancel() {
            // never issued by the service
        }
    }

}
