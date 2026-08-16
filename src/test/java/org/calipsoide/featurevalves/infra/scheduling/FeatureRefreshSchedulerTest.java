package org.calipsoide.featurevalves.infra.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.CharBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.awaitility.Awaitility;
import org.calipsoide.featurevalves.application.FeatureFile;
import org.calipsoide.featurevalves.application.FeatureFileRepository;
import org.calipsoide.featurevalves.application.FeatureLoader;
import org.calipsoide.featurevalves.application.FeatureReader;
import org.calipsoide.featurevalves.domain.ClientApplicationId;
import org.calipsoide.featurevalves.domain.Evaluator;
import org.calipsoide.featurevalves.domain.ExpositionLevel;
import org.calipsoide.featurevalves.domain.Feature;
import org.calipsoide.featurevalves.domain.FeatureId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Verifies {@link FeatureRefreshScheduler}: the initial load tick blocks
 * startup, retries with exponential backoff until it succeeds or the startup
 * timeout elapses, the fixed-delay loop survives failing ticks, and
 * {@code stop()} stops the loop, joining the in-flight tick before invoking
 * its callback (or proceeding once the stop timeout elapses).
 */
public class FeatureRefreshSchedulerTest {

    private final FeatureId id = new FeatureId(ClientApplicationId.of("app"), "feature");

    private FeatureRefreshScheduler scheduler;

    @AfterEach
    public void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    public void startRunsFirstTickBeforeStartingLoop() {
        final CountingSink sink = new CountingSink();
        final FeatureLoader loader = loader(() -> Flux.just(file()), sink);
        scheduler = scheduler(loader);

        scheduler.start();

        assertThat(sink.onNextCount()).isEqualTo(1);
        await(() -> sink.onNextCount() == 2);
    }

    @Test
    public void stopStopsTheLoop() {
        final CountingSink sink = new CountingSink();
        final FeatureLoader loader = loader(() -> Flux.just(file()), sink);
        scheduler = scheduler(loader);

        scheduler.start();
        await(() -> sink.onNextCount() == 2);
        scheduler.stop();

        final int settled = sink.onNextCount();
        Awaitility
                .await()
                .atMost(Duration.ofSeconds(3))
                .during(Duration.ofMillis(500))
                .until(() -> sink.onNextCount() == settled);
    }

    @Test
    public void stopJoinsInFlightTickBeforeCallback() throws InterruptedException {
        final CountingSink sink = new CountingSink();
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean stopped = new AtomicBoolean(false);
        final FeatureLoader loader = loader(() -> {
            if (calls.getAndIncrement() == 0) {
                return Flux.just(file());
            }
            return Mono.delay(Duration.ofMillis(600)).thenMany(Flux.just(file()));
        }, sink);
        scheduler = scheduler(loader);

        scheduler.start();
        await(() -> calls.get() >= 2);

        final long start = System.nanoTime();
        final Thread stopper = new Thread(() -> scheduler.stop(() -> stopped.set(true)));
        stopper.start();
        stopper.join(200);

        assertThat(stopped).isFalse();
        Awaitility
                .await()
                .atMost(Duration.ofSeconds(3))
                .until(stopped::get);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    public void stopProceedsWhenJoinTimesOut() {
        final CountingSink sink = new CountingSink();
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean stopped = new AtomicBoolean(false);
        final FeatureLoader loader = loader(() -> {
            if (calls.getAndIncrement() == 0) {
                return Flux.just(file());
            }
            return Mono.delay(Duration.ofSeconds(2)).thenMany(Flux.just(file()));
        }, sink);
        scheduler = scheduler(loader, Duration.ofSeconds(1), Duration.ofMillis(100));

        scheduler.start();
        await(() -> calls.get() >= 2);

        final long start = System.nanoTime();
        final Thread stopper = new Thread(() -> scheduler.stop(() -> stopped.set(true)));
        stopper.start();
        await(() -> stopped.get());

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                .isLessThan(1000);
    }

    @Test
    public void loopSurvivesFailingTicks() {
        final CountingSink sink = new CountingSink();
        final AtomicInteger calls = new AtomicInteger();
        final FeatureLoader loader = loader(() -> {
            if (calls.getAndIncrement() == 0) {
                return Flux.just(file());
            }
            return Flux.error(new IllegalStateException("source unavailable"));
        }, sink);
        scheduler = scheduler(loader);

        scheduler.start();

        assertThat(calls.get()).isEqualTo(1);
        await(() -> calls.get() >= 3);
        await(() -> sink.onErrorCount() >= 1);
    }

    @Test
    public void startAbortsWhenStartupTimesOut() {
        final CountingSink sink = new CountingSink();
        final FeatureLoader loader = loader(() -> Flux.never(), sink);
        scheduler = scheduler(loader, Duration.ofMillis(100));

        assertThatThrownBy(() -> scheduler.start())
                .hasCauseInstanceOf(TimeoutException.class);

        assertThat(sink.onNextCount()).isZero();
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    public void firstTickRetriesUntilSuccess() {
        final CountingSink sink = new CountingSink();
        final AtomicInteger calls = new AtomicInteger();
        final FeatureLoader loader = loader(() -> {
            if (calls.getAndIncrement() < 2) {
                return Flux.error(new IllegalStateException("source unavailable"));
            }
            return Flux.just(file());
        }, sink);
        scheduler = scheduler(loader);

        scheduler.start();

        assertThat(calls.get()).isEqualTo(3);
        assertThat(sink.onNextCount()).isEqualTo(1);
    }

    private FeatureRefreshScheduler scheduler(FeatureLoader loader) {
        return scheduler(loader, Duration.ofSeconds(1));
    }

    private FeatureRefreshScheduler scheduler(FeatureLoader loader, Duration startupTimeout) {
        return scheduler(loader, startupTimeout, Duration.ofSeconds(5));
    }

    private FeatureRefreshScheduler scheduler(FeatureLoader loader, Duration startupTimeout, Duration stopTimeout) {
        return new FeatureRefreshScheduler(
                loader,
                Duration.ofMillis(200),
                startupTimeout,
                stopTimeout,
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                5);
    }

    private FeatureLoader loader(FluxProvider repository, Subscriber<Feature> sink) {
        final FeatureFileRepository files = repository::flux;
        final FeatureReader reader = candidate -> Mono.just(feature());
        return new FeatureLoader(files, reader, sink);
    }

    @FunctionalInterface
    private interface FluxProvider {
        Flux<FeatureFile> flux();
    }

    private FeatureFile file() {
        return new FeatureFile(id, CharBuffer.wrap("irrelevant"));
    }

    private Feature feature() {
        final Evaluator evaluator = check -> Optional.of(ExpositionLevel.ZERO);
        return new Feature(id, Collections.emptyList(), evaluator, true);
    }

    private static void await(BooleanSupplier condition) {
        Awaitility
                .await()
                .atMost(Duration.ofSeconds(5))
                .until(condition::getAsBoolean);
    }

    private static final class CountingSink implements Subscriber<Feature> {

        private final AtomicInteger onNext = new AtomicInteger();
        private final AtomicInteger onError = new AtomicInteger();

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Feature feature) {
            onNext.incrementAndGet();
        }

        @Override
        public void onError(Throwable throwable) {
            onError.incrementAndGet();
        }

        @Override
        public void onComplete() {
            // no-op
        }

        int onNextCount() {
            return onNext.get();
        }

        int onErrorCount() {
            return onError.get();
        }
    }

}