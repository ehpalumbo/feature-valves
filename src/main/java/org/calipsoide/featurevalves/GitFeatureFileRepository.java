package org.calipsoide.featurevalves;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * A {@link FeatureFileRepository} that first refreshes the local Git clone and
 * then delegates to a {@link LocalFeatureFileRepository}.
 *
 * @see GitRepoManager#update()
 */
@Repository
@RequiredArgsConstructor
public class GitFeatureFileRepository implements FeatureFileRepository {

    private final GitRepoManager gitRepoManager;

    private final FeatureFileRepository fileRepository;

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
        gitRepoManager.update();
        return fileRepository.loadAll();
    }

}
