package org.calipsoide.featurevalves;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * A {@link FeatureFileRepository} that first refreshes the local Git clone and
 * then delegates to a {@link LocalFeatureFileRepository}.
 *
 * @see GitRepoManager#update()
 */
@Repository
public class GitFeatureFileRepository implements FeatureFileRepository {

    private GitRepoManager gitRepoManager;

    private FeatureFileRepository fileRepository;

    /**
     * Creates the git-backed repository wrapping the local one.
     *
     * @param gitRepoManager manages the local clone
     * @param fileRepository the local repository reading files from the clone
     */
    @Autowired
    public GitFeatureFileRepository(
            GitRepoManager gitRepoManager,
            LocalFeatureFileRepository fileRepository) {
        this.gitRepoManager = gitRepoManager;
        this.fileRepository = fileRepository;
    }

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
