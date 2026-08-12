package org.calipsoide.featurevalves.application;

import java.time.Duration;
import java.util.function.Consumer;

import org.calipsoide.featurevalves.domain.Feature;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Load pipeline that periodically refreshes parsed {@link Feature}s into the
 * cache.
 * <p>
 * On startup and then every {@code features.refresh.interval}, the pipeline
 * reloads all {@link FeatureFile}s from the {@link FeatureFileRepository},
 * converts each into a {@link Feature} via the {@link FeatureReader}, and
 * pushes the results into the consumer (the {@link CachingFeatureService}).
 */
@Service
@Slf4j
public class FeatureLoader implements InitializingBean {

    private FeatureFileRepository fileRepository;
    private FeatureReader featureReader;
    private Consumer<Feature> featureSink;
    private Duration refresh;

    /**
     * Creates the loader with the components of the refresh pipeline.
     *
     * @param fileRepository  source of raw feature files
     * @param featureReader   reader parsing files into features
     * @param featureSink     the consumer that receives parsed features
     * @param refreshInterval the polling interval, parsed as a {@link Duration}
     */
    @Autowired
    public FeatureLoader(
            FeatureFileRepository fileRepository,
            FeatureReader featureReader,
            Consumer<Feature> featureSink,
            @Value("${features.refresh.interval}") String refreshInterval) {
        this.fileRepository = fileRepository;
        this.featureReader = featureReader;
        this.featureSink = featureSink;
        this.refresh = Duration.parse(refreshInterval);
    }

    /**
     * Starts the refresh pipeline: an immediate load followed by periodic
     * reloads on the configured interval.
     *
     * @throws Exception if scheduling the pipeline fails
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        final Mono<Integer> now = Mono.just(0);
        final Flux<Long> timer = Flux.interval(refresh);
        Flux.concat(now, timer)
                .flatMap(time -> {
                    log.debug("Loading features configuration files.");
                    return fileRepository.loadAll();
                })
                .flatMap(file -> featureReader.read(file))
                .subscribe(featureSink);
    }

}
