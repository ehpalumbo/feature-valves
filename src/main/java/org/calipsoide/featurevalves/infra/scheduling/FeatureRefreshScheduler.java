package org.calipsoide.featurevalves.infra.scheduling;

import java.time.Duration;

import org.calipsoide.featurevalves.application.FeatureLoader;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Schedules the {@link FeatureLoader} refresh loop.
 * <p>
 * One load tick runs before the application is considered ready: it blocks
 * startup (during bean factory finalization, before the web server starts) and
 * retries with exponential backoff until the first successful tick, so no
 * request is served before the cache is populated. A first tick that exhausts
 * the retries aborts startup.
 * <p>
 * After the initial tick, a single unbounded reactive loop keeps running one
 * tick per completed tick plus a fixed delay of
 * {@code features.refresh.interval}, so ticks never overlap. Each loop tick
 * tolerates failures by resuming empty, so the loop survives a tick that
 * errors.
 *
 * @see FeatureLoader
 */
@Slf4j
@Component
public class FeatureRefreshScheduler implements SmartInitializingSingleton, DisposableBean {

    private final FeatureLoader featureLoader;
    private final Duration refreshInterval;
    private final Duration backoffMin;
    private final Duration backoffMax;
    private final long maxAttempts;

    private Disposable schedule;

    /**
     * Creates the scheduler for the given loader and tuning settings.
     *
     * @param featureLoader   the load pipeline to drive
     * @param refreshInterval fixed delay between completed ticks
     * @param backoffMin      starting backoff for the first-tick retry
     * @param backoffMax      cap for the exponential backoff
     * @param maxAttempts     first-tick attempts before giving up on startup
     */
    public FeatureRefreshScheduler(
            FeatureLoader featureLoader,
            @Value("${features.refresh.interval}") Duration refreshInterval,
            @Value("${features.refresh.backoff.min:PT1S}") Duration backoffMin,
            @Value("${features.refresh.backoff.max:PT1M}") Duration backoffMax,
            @Value("${features.refresh.backoff.max-attempts:5}") long maxAttempts) {
        this.featureLoader = featureLoader;
        this.refreshInterval = refreshInterval;
        this.backoffMin = backoffMin;
        this.backoffMax = backoffMax;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Runs the initial loading tick, retrying with exponential backoff until
     * it succeeds, then starts the fixed-delay refresh loop.
     * <p>
     * Blocks until the initial tick completes, so the application is not ready
     * to serve requests before the cache is populated. If the retries are
     * exhausted, the failure propagates and startup aborts.
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info(
                "Running initial feature load tick ({} attempts, backoff {}..{}).",
                maxAttempts, backoffMin, backoffMax);
        Mono
                .defer(featureLoader::load)
                .retryWhen(Retry.backoff(maxAttempts, backoffMin).maxBackoff(backoffMax))
                .block();
        log.info("Initial feature load tick complete; starting refresh loop with {} interval.", refreshInterval);
        schedule = Mono
                .delay(refreshInterval)
                .thenMany(Mono.defer(this::loadResiliently))
                .repeat()
                .subscribe();
    }

    /**
     * Runs one load tick, swallowing failures so the refresh loop survives.
     *
     * @return a {@code Mono} completing in lockstep with the tick
     */
    private Mono<Void> loadResiliently() {
        return featureLoader.load().onErrorResume(error -> {
            log.warn("Refresh tick failed; continuing the loop.", error);
            return Mono.empty();
        });
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

}