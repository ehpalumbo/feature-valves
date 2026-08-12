package org.calipsoide.featurevalves;

/**
 * Created by epalumbo on 9/16/17.
 */
public record ClientApplicationId(String name) {

    public ClientApplicationId {
        name = name.toLowerCase();
    }

    public static ClientApplicationId of(String name) {
        return new ClientApplicationId(name);
    }

    @Override
    public String toString() {
        return name;
    }

}
