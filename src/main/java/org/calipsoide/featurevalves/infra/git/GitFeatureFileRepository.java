package org.calipsoide.featurevalves.infra.git;

import org.calipsoide.featurevalves.application.FeatureFile;
import org.calipsoide.featurevalves.application.FeatureFileRepository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * A {@link FeatureFileRepository} that first refreshes the local Git clone and
 * then delegates to a {@link LocalFeatureFileRepository}.
 * <p>
 * Marked {@link Primary} so that the {@code FeatureFileRepository} port resolves
 * to this decorator (rather than the leaf {@link LocalFeatureFileRepository} it
 * wraps) for application-layer consumers.
 * <p>
 * The blocking JGit update is offloaded to a bounded-elastic scheduler so it
 * does not hold a reactive pipeline thread.
 *
 * @see GitRepoManager#update()
 */
@Repository
@Primary
@RequiredArgsConstructor
public class GitFeatureFileRepository implements FeatureFileRepository {

    private final GitRepoManager gitRepoManager;

    private final LocalFeatureFileRepository fileRepository;

    /**
     * {@inheritDoc}
     * <p>
     * The local clone is updated first, so the returned {@link Flux} reflects
     * the latest committed feature definitions.
     *
     * @return a {@link Flux} over the refreshed {@link FeatureFile}s
     */
    @Override
    public Flux<FeatureFile> loadAll() {
        return Mono
                .fromRunnable(gitRepoManager::update)
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(fileRepository.loadAll());
    }

}
