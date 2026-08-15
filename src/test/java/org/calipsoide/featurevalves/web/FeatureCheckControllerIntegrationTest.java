package org.calipsoide.featurevalves.web;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    @Order(1)
    public void alwaysOnFeatureReturnsTrue() throws Exception {
        waitUntilLoaded();
        webTestClient.post()
                .uri(ALWAYS_ON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result").isEqualTo(true);
    }

    @Test
    @Order(2)
    public void alwaysOffFeatureReturnsFalse() throws Exception {
        waitUntilLoaded();
        webTestClient.post()
                .uri(ALWAYS_OFF)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result").isEqualTo(false);
    }

    @Test
    @Order(3)
    public void nonMatchingTagsReturnFalseNot404() throws Exception {
        waitUntilLoaded();
        webTestClient.post()
                .uri(ALWAYS_ON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(NON_MATCHING_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result").isEqualTo(false);
    }

    @Test
    @Order(4)
    public void unknownFeatureReturns404() throws Exception {
        waitUntilLoaded();
        webTestClient.post()
                .uri(UNKNOWN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    public void removedFeatureReturns404AfterRefresh() throws Exception {
        waitUntilLoaded();
        try (Git git = Git.open(temporaryFolder.resolve("origin").toFile())) {
            git.rm().addFilepattern("features/app/always-off.yml").call();
            final var identity = new PersonIdent("feature-valves", "feature-valves@example.org");
            git.commit().setAuthor(identity).setCommitter(identity)
                    .setMessage("remove always-off").call();
        }
        waitUntilRemoved();
    }

    private void waitUntilLoaded() throws Exception {
        final long deadline = System.currentTimeMillis() + 15_000;
        while (statusOf(ALWAYS_ON, MATCHING_BODY) != HttpStatus.OK) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("features were not loaded within 15s");
            }
            Thread.sleep(500);
        }
    }

    private void waitUntilRemoved() throws Exception {
        final long deadline = System.currentTimeMillis() + 15_000;
        while (statusOf(ALWAYS_OFF, MATCHING_BODY) != HttpStatus.NOT_FOUND) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("removed feature still served after 15s");
            }
            Thread.sleep(500);
        }
    }

    private HttpStatusCode statusOf(String uri, String body) {
        return webTestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus();
    }

    private static void writeYaml(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(Charset.defaultCharset()));
    }

}
