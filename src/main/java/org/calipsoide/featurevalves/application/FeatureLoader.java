package org.calipsoide.featurevalves.application;

import org.calipsoide.featurevalves.domain.Feature;
import org.reactivestreams.Subscriber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Load pipeline that refreshes parsed {@link Feature}s into the cache.
 * <p>
 * One {@link #load()} run reloads all {@link FeatureFile}s from the
 * {@link FeatureFileRepository}, converts each into a {@link Feature} via the
 * {@link FeatureReader}, and pushes the results into the subscriber (the
 * {@link InMemoryFeatureService}), which reconciles the cache against the
 * repository when the run completes. The returned {@code Mono} completes (or
 * errors) in lockstep with that run; cadence and retry concerns belong to the
 * caller, see {@code FeatureRefreshScheduler}.
 */
@Service
@Slf4j
public class FeatureLoader {

    private final FeatureFileRepository fileRepository;
    private final FeatureReader featureReader;
    private final Subscriber<Feature> featureSink;

    /**
     * Creates the loader with the components of the refresh pipeline.
     *
     * @param fileRepository source of raw feature files
     * @param featureReader  reader parsing files into features
     * @param featureSink    the subscriber that receives parsed features
     */
    @Autowired
    public FeatureLoader(
            FeatureFileRepository fileRepository,
            FeatureReader featureReader,
            Subscriber<Feature> featureSink) {
        this.fileRepository = fileRepository;
        this.featureReader = featureReader;
        this.featureSink = featureSink;
    }

    /**
     * Runs one full refresh, completing only when the run's subscription to
     * the sink completes, and failing when the underlying source fails.
     * <p>
     * Whole-source failures (e.g. the repository cannot be read) surface as
     * errors in the returned {@code Mono}; a single malformed file is skipped
     * instead of aborting the run.
     *
     * @return a {@code Mono} that completes in lockstep with the run
     */
    public Mono<Void> load() {
        final Sinks.Empty<Void> done = Sinks.empty();
        loadFeatures()
                .doOnComplete(done::tryEmitEmpty)
                .doOnError(done::tryEmitError)
                .subscribe(featureSink);
        return done.asMono();
    }

    /**
     * The per-run stream of parsed features.
     * <p>
     * Files that fail to parse are skipped so a single broken definition does
     * not abort the whole refresh; the {@link InMemoryFeatureService} evicts
     * them on run completion because they are no longer seen.
     *
     * @return a {@link Flux} of the parsed {@link Feature}s in this run
     */
    private Flux<Feature> loadFeatures() {
        final var files = fileRepository.loadAll();
        return files.flatMap(file -> featureReader.read(file).onErrorResume(error -> {
            log.warn("Skipping {}: {}", file.id(), error.toString());
            return Mono.empty();
        }));
    }

}