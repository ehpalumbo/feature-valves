package org.calipsoide.featurevalves.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.CharBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * Verifies {@link FeatureLoader#load()}: files stream through the reader into
 * features, and a file that fails to parse is skipped rather than aborting the
 * run.
 */
public class FeatureLoaderTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    @Test
    public void loadStreamsParsedFeatures() {
        final FeatureFile file = new FeatureFile(id, CharBuffer.wrap("irrelevant"));
        final Feature expected = feature();
        final RecordingSink sink = new RecordingSink();
        final FeatureLoader loader = new FeatureLoader(
                () -> Flux.just(file),
                candidate -> Mono.just(expected),
                sink);
        StepVerifier
                .create(loader.load())
                .verifyComplete();
        assertThat(sink.features).containsExactly(expected);
    }

    @Test
    public void loadSkipsMalformedFiles() {
        final FeatureFile bad = new FeatureFile(id, CharBuffer.wrap("broken"));
        final RecordingSink sink = new RecordingSink();
        final FeatureLoader loader = new FeatureLoader(
                () -> Flux.just(bad),
                candidate -> Mono.error(new IllegalArgumentException("cannot parse " + candidate.id())),
                sink);
        StepVerifier
                .create(loader.load())
                .verifyComplete();
        assertThat(sink.features).isEmpty();
    }

    private Feature feature() {
        final Evaluator evaluator = check -> Optional.of(ExpositionLevel.ZERO);
        return new Feature(id, Collections.emptyList(), evaluator, true);
    }

    private static final class RecordingSink implements Subscriber<Feature> {

        private final List<Feature> features = new CopyOnWriteArrayList<>();

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Feature feature) {
            features.add(feature);
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