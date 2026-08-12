package org.calipsoide.featurevalves.application;

import java.nio.CharBuffer;

import org.calipsoide.featurevalves.domain.FeatureId;

/**
 * A raw feature definition file as loaded from a repository: its
 * {@link FeatureId} and decoded text content.
 *
 * @param id     the feature the file defines
 * @param buffer the file content
 * @see FeatureFileRepository#loadAll()
 */
public record FeatureFile(FeatureId id, CharBuffer buffer) {

}
