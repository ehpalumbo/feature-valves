package org.calipsoide.featurevalves.application;

import org.calipsoide.featurevalves.domain.Feature;

import reactor.core.publisher.Mono;

/**
 * Port for converting a raw {@link FeatureFile} into a domain {@link Feature}.
 * <p>
 * The load pipeline depends on this seam instead of any concrete parser, so
 * feature definition sources and formats can be swapped without touching the
 * application layer.
 *
 * @see org.calipsoide.featurevalves.infra.yaml.YamlFileFeatureFactory
 */
public interface FeatureReader {

    /**
     * Parses the given feature file into a {@link Feature}.
     *
     * @param file the raw feature file to parse
     * @return a {@code Mono} of the resulting {@link Feature}
     */
    Mono<Feature> read(FeatureFile file);

}
