package org.calipsoide.featurevalves;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the Feature Valves service.
 *
 * @see FeatureCheckController
 */
@SpringBootApplication
public class Application {

    /**
     * Creates the application context (no explicit configuration).
     */
    public Application() {
    }

    /**
     * Bootstraps the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
