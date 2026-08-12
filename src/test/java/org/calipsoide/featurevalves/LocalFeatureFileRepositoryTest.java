package org.calipsoide.featurevalves;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by epalumbo on 9/17/17.
 */
public class LocalFeatureFileRepositoryTest {

    @TempDir
    public Path baseFolder;

    private LocalFeatureFileRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        repository = new LocalFeatureFileRepository(baseFolder.toString());
    }

    private static Path write(Path path, String content) throws IOException {
        return Files.write(path, content.getBytes(Charset.defaultCharset()));
    }

    @Test
    public void testLoadAll() throws Exception {
        final Path folder = Files.createDirectory(baseFolder.resolve("app"));
        final Path first = folder.resolve("first-feature.yml");
        final Path second = folder.resolve("second-feature.yml");
        write(first, "active: true");
        write(second, "active: false");
        final ClientApplicationId applicationId = ClientApplicationId.of(folder.getFileName().toString());
        StepVerifier
                .create(repository.loadAll())
                .assertNext(file -> {
                    assertThat(file).isNotNull();
                    assertThat(file.id()).isEqualTo(new FeatureId(applicationId, "first-feature"));
                    assertThat(file.buffer().toString()).isEqualTo("active: true");
                })
                .assertNext(file -> {
                    assertThat(file).isNotNull();
                    assertThat(file.id()).isEqualTo(new FeatureId(applicationId, "second-feature"));
                    assertThat(file.buffer().toString()).isEqualTo("active: false");
                })
                .verifyComplete();
    }

    @Test
    public void testNoMatchingFiles() throws Exception {
        final Path folder = Files.createDirectory(baseFolder.resolve("app"));
        final Path other = folder.resolve("other-file.txt");
        write(other, "nothing really important");
        StepVerifier
                .create(repository.loadAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    public void testYmlAndYamlFilesBothPickedUpInFilenameOrder() throws Exception {
        final Path folder = Files.createDirectory(baseFolder.resolve("app"));
        final Path first = folder.resolve("a.yml");
        final Path second = folder.resolve("b.yaml");
        write(first, "active: true");
        write(second, "active: false");
        final ClientApplicationId applicationId = ClientApplicationId.of(folder.getFileName().toString());
        StepVerifier
                .create(repository.loadAll())
                .assertNext(file -> {
                    assertThat(file.id()).isEqualTo(new FeatureId(applicationId, "a"));
                    assertThat(file.buffer().toString()).isEqualTo("active: true");
                })
                .assertNext(file -> {
                    assertThat(file.id()).isEqualTo(new FeatureId(applicationId, "b"));
                    assertThat(file.buffer().toString()).isEqualTo("active: false");
                })
                .verifyComplete();
    }

    @Test
    public void testRootLevelFileIsNotAnAppDirectory() throws Exception {
        write(baseFolder.resolve("root-note.txt"), "just a file");
        StepVerifier
                .create(repository.loadAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    public void testMissingRootPathYieldsError() throws Exception {
        final var missing =
                new LocalFeatureFileRepository(baseFolder.resolve("does-not-exist").toString());
        StepVerifier
                .create(missing.loadAll())
                .expectError()
                .verify();
    }

}