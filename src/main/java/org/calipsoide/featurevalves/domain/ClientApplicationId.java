package org.calipsoide.featurevalves.domain;

/**
 * Identifies a client application that consumes the feature valve service.
 * <p>
 * The name is normalized to lower case so comparisons are case-insensitive.
 *
 * @param name the application name, normalized to lower case
 */
public record ClientApplicationId(String name) {

    /**
     * Normalizes the name to lower case during construction.
     */
    public ClientApplicationId {
        name = name.toLowerCase();
    }

    /**
     * Creates a {@code ClientApplicationId} from the given name.
     *
     * @param name the application name
     * @return an identifier carrying the lower-cased name
     */
    public static ClientApplicationId of(String name) {
        return new ClientApplicationId(name);
    }

    @Override
    public String toString() {
        return name;
    }

}
