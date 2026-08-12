package org.calipsoide.featurevalves;

/**
 * Created by epalumbo on 9/16/17.
 */
public record Tag(String code, String value) {

    @Override
    public String toString() {
        return code + ":" + value;
    }

}