package org.calipsoide.featurevalves.application;

import java.time.Duration;

import org.calipsoide.featurevalves.domain.Feature;
import org.reactivestreams.Subscriber;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Load pipeline that periodically refreshes parsed {@link Feature}s into the
 * cache.
 * <p>
 * One tick runs immediately on startup and then again after each completed tick
 * plus a fixed delay of {@code features.refresh.interval}, so ticks never
 * overlap. Each tick reloads all {@link FeatureFile}s from the
 * {@link FeatureFileRepository}, converts each into a {@link Feature} via the
 * {@link FeatureReader}, and pushes the results into the subscriber (the
 * {@link InMemoryFeatureService}), which reconciles the cache against the
 * repository on tick completion.
 */
@Service
@Slf4j
public class FeatureLoader implements InitializingBean, DisposableBean {

    private final FeatureFileRepository fileRepository;
    private final FeatureReader featureReader;
    private final Subscriber<Feature> featureSink;
    private final Duration refreshInterval;

    private Disposable schedule;

    /**
     * Creates the loader with the components of the refresh pipeline.
     *
     * @param fileRepository  source of raw feature files
     * @param featureReader   reader parsing files into features
     * @param featureSink     the subscriber that receives parsed features
     * @param refreshInterval the polling interval
     */
    @Autowired
    public FeatureLoader(
            FeatureFileRepository fileRepository,
            FeatureReader featureReader,
            Subscriber<Feature> featureSink,
            @Value("${features.refresh.interval}") Duration refreshInterval) {
        this.fileRepository = fileRepository;
        this.featureReader = featureReader;
        this.featureSink = featureSink;
        this.refreshInterval = refreshInterval;
    }

    /**
     * Starts the refresh loop: an immediate tick followed by a tick after each
     * previous tick completes plus the configured fixed delay.
     */
    @Override
    public void afterPropertiesSet() {
        if (schedule != null) {
            schedule.dispose();
        }
        log.info("Starting feature refresh loop with {} interval.", refreshInterval);
        schedule = Mono
                .defer(this::tick)
                .delayElement(refreshInterval)
                .repeat()
                .subscribe();
    }

    /**
     * Stops the refresh loop by cancelling the subscription to the tick stream.
     */
    @Override
    public void destroy() {
        log.info("Stopping feature refresh loop.");
        if (schedule != null) {
            schedule.dispose();
        }
    }

    /**
     * Runs one full refresh tick, completing only when the tick's subscription
     * to the sink completes.
     *
     * @return a {@code Mono} that completes in lockstep with the tick
     */
    private Mono<Long> tick() {
        final Sinks.One<Long> done = Sinks.one();
        loadFeatures()
                .doOnComplete(() -> done.tryEmitValue(1L))
                .doOnError(ignored -> done.tryEmitValue(1L))
                .subscribe(featureSink);
        return done.asMono();
    }

    /**
     * The per-tick stream of parsed features.
     * <p>
     * Files that fail to parse are skipped so a single broken definition does
     * not abort the whole refresh; the {@link InMemoryFeatureService} evicts
     * them on tick completion because they are no longer seen.
     *
     * @return a {@link Flux} of the parsed {@link Feature}s in this tick
     */
    Flux<Feature> loadFeatures() {
        final var files = fileRepository.loadAll();
        return files.flatMap(file -> featureReader.read(file).onErrorResume(error -> {
            log.warn("Skipping {}: {}", file.id(), error.toString());
            return Mono.empty();
        }));
    }

}
