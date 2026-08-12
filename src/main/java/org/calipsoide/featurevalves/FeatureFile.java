package org.calipsoide.featurevalves;

import java.nio.CharBuffer;

/**
 * A raw feature definition file as loaded from a repository: its
 * {@link FeatureId} and decoded text content.
 *
 * @param id     the feature the file defines
 * @param buffer the file content
 * @see FeatureFileRepository#loadAll()
 * @see YamlFileFeatureFactory
 */
public record FeatureFile(FeatureId id, CharBuffer buffer) {

}
