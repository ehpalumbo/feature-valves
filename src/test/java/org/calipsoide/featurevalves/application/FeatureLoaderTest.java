package org.calipsoide.featurevalves.application;

import java.nio.CharBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import org.calipsoide.featurevalves.domain.ClientApplicationId;
import org.calipsoide.featurevalves.domain.Evaluator;
import org.calipsoide.featurevalves.domain.ExpositionLevel;
import org.calipsoide.featurevalves.domain.Feature;
import org.calipsoide.featurevalves.domain.FeatureId;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies {@link FeatureLoader#loadFeatures()}: files stream through the
 * reader into features, and a file that fails to parse is skipped rather
 * than aborting the tick.
 */
public class FeatureLoaderTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    @Test
    public void tickStreamsParsedFeatures() {
        final FeatureFile file = new FeatureFile(id, CharBuffer.wrap("irrelevant"));
        final Feature expected = feature();
        final FeatureLoader loader = new FeatureLoader(
                () -> Flux.just(file),
                candidate -> Mono.just(expected),
                new RecordingSink(),
                Duration.ofHours(1));
        StepVerifier
                .create(loader.loadFeatures())
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    public void tickSkipsMalformedFiles() {
        final FeatureFile bad = new FeatureFile(id, CharBuffer.wrap("broken"));
        final FeatureLoader loader = new FeatureLoader(
                () -> Flux.just(bad),
                candidate -> Mono.error(new IllegalArgumentException("cannot parse " + candidate.id())),
                new RecordingSink(),
                Duration.ofHours(1));
        StepVerifier
                .create(loader.loadFeatures())
                .verifyComplete();
    }

    private Feature feature() {
        final Evaluator evaluator = check -> Optional.of(ExpositionLevel.ZERO);
        return new Feature(id, Collections.emptyList(), evaluator, true);
    }

    private static final class RecordingSink implements Subscriber<Feature> {

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Feature feature) {
            // no-op
        }

        @Override
        public void onError(Throwable throwable) {
            // no-op
        }

        @Override
        public void onComplete() {
            // no-op
        }
    }

}
