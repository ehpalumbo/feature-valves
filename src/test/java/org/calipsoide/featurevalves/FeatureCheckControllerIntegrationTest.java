package org.calipsoide.featurevalves;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

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
    public static void setUpOriginAndSystemProperties() throws Exception {
        final Path origin = Files.createDirectory(temporaryFolder.resolve("origin"));
        final Path local = temporaryFolder.resolve("local");
        try (Git git = Git.init().setDirectory(origin.toFile()).call()) {
            writeYaml(origin.resolve("features/app/always-on.yml"),
                    "active: true\neval:\n  - name\nvalves:\n  - name: on\n    tags:\n      t: x\n    value: 100\n");
            writeYaml(origin.resolve("features/app/always-off.yml"),
                    "active: true\neval:\n  - name\nvalves:\n  - name: off\n    tags:\n      t: x\n    value: 0\n");
            git.add().addFilepattern(".").call();
            final PersonIdent identity = new PersonIdent("feature-valves", "feature-valves@example.org");
            git.commit().setAuthor(identity).setCommitter(identity).setMessage("initial features").call();
        }
        System.setProperty("features.git.remote.url", origin.toString());
        System.setProperty("features.git.local.path", local.toString());
        System.setProperty("features.git.local.data", local.resolve("features").toString());
        System.setProperty("features.git.remote.branch", "master");
        System.setProperty("features.cache.ttl", "PT1H");
        System.setProperty("features.refresh.interval", "PT1H");
    }

    @Test
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
    public void unknownFeatureReturns404() throws Exception {
        waitUntilLoaded();
        webTestClient.post()
                .uri(UNKNOWN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MATCHING_BODY)
                .exchange()
                .expectStatus().isNotFound();
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
