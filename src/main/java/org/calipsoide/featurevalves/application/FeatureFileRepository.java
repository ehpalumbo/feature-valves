package org.calipsoide.featurevalves.application;

import reactor.core.publisher.Flux;

/**
 * Source of raw feature definition files, the seam that decouples the load
 * pipeline from where files are stored.
 *
 * @see org.calipsoide.featurevalves.infra.git.LocalFeatureFileRepository
 * @see org.calipsoide.featurevalves.infra.git.GitFeatureFileRepository
 */
public interface FeatureFileRepository {

    /**
     * Loads all available feature files.
     *
     * @return a {@link Flux} of the loaded {@link FeatureFile}s, which may
     *         emit an error if the underlying source cannot be read
     */
    Flux<FeatureFile> loadAll();

}
