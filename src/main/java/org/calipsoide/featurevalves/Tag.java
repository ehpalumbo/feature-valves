package org.calipsoide.featurevalves;

/**
 * A single key-value pair of request data.
 * <p>
 * Tags are the raw material of a {@link FeatureCheck}. A {@link FeatureValve}
 * matches a check when every one of its required tags is present among the
 * check's tags.
 *
 * @param code  the tag key (e.g. {@code animal})
 * @param value the tag value (e.g. {@code cat})
 */
public record Tag(String code, String value) {

    @Override
    public String toString() {
        return code + ":" + value;
    }

}
