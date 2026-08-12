package org.calipsoide.featurevalves;

import java.time.Duration;

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
 * converts each into a {@link Feature} via the {@link YamlFileFeatureFactory},
 * and pushes the results into the {@link CachingFeatureService}.
 */
@Service
@Slf4j
public class FeatureLoader implements InitializingBean {

    private FeatureFileRepository fileRepository;
    private YamlFileFeatureFactory featureFactory;
    private CachingFeatureService cachingService;
    private Duration refresh;

    /**
     * Creates the loader with the components of the refresh pipeline.
     *
     * @param fileRepository   source of raw feature files
     * @param featureFactory   factory parsing files into features
     * @param cachingService   the cache that receives parsed features
     * @param refreshInterval  the polling interval, parsed as a {@link Duration}
     */
    @Autowired
    public FeatureLoader(
            GitFeatureFileRepository fileRepository,
            YamlFileFeatureFactory featureFactory,
            CachingFeatureService cachingService,
            @Value("${features.refresh.interval}") String refreshInterval) {
        this.fileRepository = fileRepository;
        this.featureFactory = featureFactory;
        this.cachingService = cachingService;
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
                .flatMap(file -> featureFactory.read(file))
                .subscribe(cachingService);
    }

}
