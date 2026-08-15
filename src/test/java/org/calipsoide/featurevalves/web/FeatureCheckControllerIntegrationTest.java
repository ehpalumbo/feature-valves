package org.calipsoide.featurevalves.web;

import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Full-context integration test driving the {@link FeatureCheckController}
 * endpoint against a real local JGit repo: ON/OFF evaluation, non-matching
 * tags yielding {@code 200/false}, and unknown features yielding {@code 404}.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class FeatureCheckControllerIntegrationTest {

    @TempDir
    public static Path temporaryFolder;

    @Autowired
    private WebTestClient webTestClient;

    private static final String ALWAYS_ON = "/feature_valves/app/always-on/checks";
    private static final String ALWAYS_OFF = "/feature_valves/app/always-off/checks";
    private static final String UNKNOWN = "/feature_valves/app/nope/checks";

    private static final String MATCHING_BODY = "{\"name\":\"x\",\"t\":\"x\"}";
    private static final String NON_MATCHING_BODY = "{\"other\":\"y\"}";

    @BeforeAll
    public static void setUpOrigin() throws Exception {
        final Path origin = Files.createDirectory(temporaryFolder.resolve("origin"));
        try (Git git = Git.init().setDirectory(origin.toFile()).call()) {
            writeYaml(origin.resolve("features/app/always-on.yml"), """
                    active: true
                    eval:
                      - name
                    valves:
                      - name: on
                        tags:
                          t: x
                        value: 100
                    """);
            writeYaml(origin.resolve("features/app/always-off.yml"), """
                    active: true
                    eval:
                      - name
                    valves:
                      - name: off
                        tags:
                          t: x
                        value: 0
                    """);
            git.add().addFilepattern(".").call();
            final var identity = new PersonIdent("feature-valves", "feature-valves@example.org");
            git.commit().setAuthor(identity).setCommitter(identity).setMessage("initial features").call();
        }
    }

    @DynamicPropertySource
    public static void registerProperties(DynamicPropertyRegistry registry) {
        final Path local = temporaryFolder.resolve("local");
        registry.add("features.git.remote.url", () -> temporaryFolder.resolve("origin").toString());
        registry.add("features.git.local.path", local::toString);
        registry.add("features.git.local.data", () -> local.resolve("features").toString());
        registry.add("features.git.remote.branch", () -> "master");
        registry.add("features.cache.ttl", () -> "PT1H");
        registry.add("features.refresh.interval", () -> "PT2S");
    }

    @Test
    public void alwaysOnFeatureReturnsTrue() {
        waitUntilStatus(ALWAYS_ON, MATCHING_BODY, HttpStatus.OK);
        webTestClient.post()
                .uri(ALWAYS_ON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result").isEqualTo(true);
    }

    @Test
    public void alwaysOffFeatureReturnsFalse() {
        waitUntilStatus(ALWAYS_OFF, MATCHING_BODY, HttpStatus.OK);
        webTestClient.post()
                .uri(ALWAYS_OFF)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result").isEqualTo(false);
    }

    @Test
    public void nonMatchingTagsReturnFalseNot404() {
        waitUntilStatus(ALWAYS_ON, MATCHING_BODY, HttpStatus.OK);
        webTestClient.post()
                .uri(ALWAYS_ON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(NON_MATCHING_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result").isEqualTo(false);
    }

    @Test
    public void unknownFeatureReturns404() {
        webTestClient.post()
                .uri(UNKNOWN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    public void removedFeatureReturns404AfterRefresh() throws Exception {
        final String uri = featureUri("removed");
        addFeature("removed", """
                active: true
                eval:
                  - name
                valves:
                  - name: on
                    tags:
                      t: x
                    value: 100
                """);
        waitUntilStatus(uri, MATCHING_BODY, HttpStatus.OK);
        removeFeature("removed");
        waitUntilStatus(uri, MATCHING_BODY, HttpStatus.NOT_FOUND);
    }

    private void waitUntilStatus(String uri, String body, HttpStatus expected) {
        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> webTestClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .exchange()
                        .expectStatus().isEqualTo(expected));
    }

    private static String featureUri(String featureCode) {
        return "/feature_valves/app/" + featureCode + "/checks";
    }

    private static void addFeature(String featureCode, String yaml) throws Exception {
        commit(featureCode, yaml, true);
    }

    private static void removeFeature(String featureCode) throws Exception {
        commit(featureCode, null, false);
    }

    private static void commit(String featureCode, String yaml, boolean add) throws Exception {
        try (Git git = Git.open(temporaryFolder.resolve("origin").toFile())) {
            final String relative = "features/app/" + featureCode + ".yml";
            if (add) {
                final Path file = git.getRepository().getWorkTree().toPath().resolve(relative);
                Files.createDirectories(file.getParent());
                Files.write(file, yaml.getBytes(Charset.defaultCharset()));
                git.add().addFilepattern(relative).call();
            } else {
                git.rm().addFilepattern(relative).call();
            }
            final var identity = new PersonIdent("feature-valves", "feature-valves@example.org");
            git.commit().setAuthor(identity).setCommitter(identity)
                    .setMessage((add ? "add " : "remove ") + featureCode).call();
        }
    }

    private static void writeYaml(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(Charset.defaultCharset()));
    }

}
