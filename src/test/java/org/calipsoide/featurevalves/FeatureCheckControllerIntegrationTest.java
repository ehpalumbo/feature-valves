package org.calipsoide.featurevalves;

import com.google.common.io.Files;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.nio.charset.Charset;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class FeatureCheckControllerIntegrationTest {

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Autowired
    private WebTestClient webTestClient;

    private static final String ALWAYS_ON = "/feature_valves/app/always-on/checks";
    private static final String ALWAYS_OFF = "/feature_valves/app/always-off/checks";
    private static final String UNKNOWN = "/feature_valves/app/nope/checks";

    private static final String MATCHING_BODY = "{\"name\":\"x\",\"t\":\"x\"}";
    private static final String NON_MATCHING_BODY = "{\"other\":\"y\"}";

    @BeforeClass
    public static void setUpOriginAndSystemProperties() throws Exception {
        final File origin = temporaryFolder.newFolder("origin");
        final File local = new File(temporaryFolder.getRoot(), "local");
        try (Git git = Git.init().setDirectory(origin).call()) {
            writeYaml(new File(origin, "features/app/always-on.yml"),
                    "active: true\neval:\n  - name\nvalves:\n  - name: on\n    tags:\n      t: x\n    value: 100\n");
            writeYaml(new File(origin, "features/app/always-off.yml"),
                    "active: true\neval:\n  - name\nvalves:\n  - name: off\n    tags:\n      t: x\n    value: 0\n");
            git.add().addFilepattern(".").call();
            final PersonIdent identity = new PersonIdent("feature-valves", "feature-valves@example.org");
            git.commit().setAuthor(identity).setCommitter(identity).setMessage("initial features").call();
        }
        System.setProperty("features.git.remote.url", origin.getAbsolutePath());
        System.setProperty("features.git.local.path", local.getAbsolutePath());
        System.setProperty("features.git.local.data", new File(local, "features").getAbsolutePath());
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
                .syncBody(MATCHING_BODY)
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
                .syncBody(MATCHING_BODY)
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
                .syncBody(NON_MATCHING_BODY)
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
                .syncBody(MATCHING_BODY)
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

    private HttpStatus statusOf(String uri, String body) {
        return webTestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .syncBody(body)
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus();
    }

    private static void writeYaml(File file, String content) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(content, file, Charset.defaultCharset());
    }

}
