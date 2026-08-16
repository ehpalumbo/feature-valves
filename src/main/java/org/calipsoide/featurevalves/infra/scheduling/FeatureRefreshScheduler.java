package org.calipsoide.featurevalves.infra.scheduling;

import java.time.Duration;

import org.calipsoide.featurevalves.application.FeatureLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Schedules the {@link FeatureLoader} refresh loop.
 * <p>
 * {@link #start()} runs one load tick before the refresh loop begins: it blocks
 * startup (during the context's lifecycle phase, before the web server, which
 * is started last) and retries with exponential backoff until the first
 * successful tick, or until the startup timeout elapses, so no request is
 * served before the cache is populated. A first tick that exhausts the retries
 * or the timeout aborts startup.
 * <p>
 * After the initial tick, a single unbounded reactive loop keeps running one
 * tick per completed tick plus a fixed delay of
 * {@code features.refresh.interval}, so ticks never overlap. Each loop tick
 * tolerates failures by resuming empty, so the loop survives a tick that
 * errors.
 * <p>
 * The loop is a {@link SmartLifecycle} component so it can be stopped in an
 * ordered way: {@link #stop(Runnable)} disposes the loop and waits for the
 * in-flight tick to finish — bounded by {@code features.refresh.stop-timeout}
 * — before invoking the callback, so no fetch runs concurrently with
 * {@code GitRepoManager} shutdown. It participates in Spring's test context
 * pausing (see the {@code spring.test.context.cache.pause} property): when the
 * context is paused, the loop stops, and it restarts with the context via
 * {@link #start()}.
 *
 * @see FeatureLoader
 */
@Slf4j
@Component
public class FeatureRefreshScheduler implements SmartLifecycle {

    private final FeatureLoader featureLoader;
    private final Duration refreshInterval;
    private final Duration startupTimeout;
    private final Duration stopTimeout;
    private final Duration backoffMin;
    private final Duration backoffMax;
    private final long maxAttempts;

    private volatile Disposable schedule;
    private volatile Mono<Void> inFlightTick;

    /**
     * Creates the scheduler for the given loader and tuning settings.
     *
     * @param featureLoader   the load pipeline to drive
     * @param refreshInterval fixed delay between completed ticks
     * @param startupTimeout  upper bound on the blocking startup tick
     * @param stopTimeout     upper bound on waiting for an in-flight tick when
     *                        stopping
     * @param backoffMin      starting backoff for the startup-tick retry
     * @param backoffMax      cap for the exponential backoff
     * @param maxAttempts     total startup attempts before giving up
     */
    public FeatureRefreshScheduler(
            FeatureLoader featureLoader,
            @Value("${features.refresh.interval}") Duration refreshInterval,
            @Value("${features.refresh.startup-timeout:PT1M}") Duration startupTimeout,
            @Value("${features.refresh.stop-timeout:PT5S}") Duration stopTimeout,
            @Value("${features.refresh.backoff.min:PT1S}") Duration backoffMin,
            @Value("${features.refresh.backoff.max:PT1M}") Duration backoffMax,
            @Value("${features.refresh.backoff.max-attempts:5}") long maxAttempts) {
        this.featureLoader = featureLoader;
        this.refreshInterval = refreshInterval;
        this.startupTimeout = startupTimeout;
        this.stopTimeout = stopTimeout;
        this.backoffMin = backoffMin;
        this.backoffMax = backoffMax;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Runs the initial loading tick, retrying with exponential backoff until it
     * succeeds or the startup timeout elapses, then starts the fixed-delay
     * refresh loop.
     * <p>
     * Called by the context's lifecycle processor at startup — before the web
     * server, which is started last — and again when a paused context is
     * resumed. Blocks until the initial tick completes, so the application is
     * not ready to serve requests before the cache is populated. If the retries
     * or the timeout are exhausted, the failure propagates and startup aborts.
     */
    @Override
    public void start() {
        if (schedule != null) {
            return;
        }
        log.info(
                "Running initial feature load tick (up to {} attempts, {} startup timeout, backoff {}..{}).",
                maxAttempts, startupTimeout, backoffMin, backoffMax);
        Mono
                .defer(featureLoader::load)
                .retryWhen(Retry.backoff(retries(), backoffMin).maxBackoff(backoffMax))
                .timeout(startupTimeout)
                .block();
        log.info("Initial feature load complete; starting refresh loop with {} interval.", refreshInterval);
        startLoop();
    }

    /**
     * Reports whether the refresh loop is currently running.
     *
     * @return {@code true} when the loop has been started and not yet stopped
     */
    @Override
    public boolean isRunning() {
        return schedule != null;
    }

    /**
     * Stops the refresh loop by cancelling the subscription to the tick stream,
     * then waits (bounded) for the in-flight tick to finish before invoking the
     * given callback. The callback is invoked exactly once.
     *
     * @param callback invoked once the loop and any in-flight tick have stopped
     */
    @Override
    public void stop(Runnable callback) {
        log.info("Stopping feature refresh loop.");
        final Disposable current = schedule;
        schedule = null;
        if (current != null) {
            current.dispose();
        }
        final Mono<Void> tick = inFlightTick;
        if (tick == null) {
            callback.run();
            return;
        }
        tick
                .timeout(stopTimeout)
                .onErrorResume(error -> {
                    log.warn("Refresh tick did not finish in time; proceeding with shutdown.", error);
                    return Mono.empty();
                })
                .doFinally(signal -> callback.run())
                .subscribe();
    }

    /**
     * Stops the refresh loop without waiting for an in-flight tick to finish.
     */
    @Override
    public void stop() {
        stop(() -> {
        });
    }

    /**
     * The number of retries implied by the configured total attempt count.
     *
     * @return {@code maxAttempts - 1}, clamped to zero
     */
    private long retries() {
        return Math.max(0L, maxAttempts - 1L);
    }

    /**
     * Starts the fixed-delay refresh loop, one tick per completed tick plus a
     * fixed delay.
     */
    private void startLoop() {
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
        final Mono<Void> tick = featureLoader.load()
                .onErrorResume(error -> {
                    log.warn("Refresh tick failed; continuing the loop.", error);
                    return Mono.empty();
                });
        inFlightTick = tick;
        return tick;
    }

}