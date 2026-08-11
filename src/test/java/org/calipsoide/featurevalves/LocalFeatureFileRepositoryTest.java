package org.calipsoide.featurevalves;

import com.google.common.io.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import reactor.test.StepVerifier;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by epalumbo on 9/17/17.
 */
public class LocalFeatureFileRepositoryTest {

    @Rule
    public TemporaryFolder baseFolder = new TemporaryFolder();

    private LocalFeatureFileRepository repository;

    @Before
    public void setUp() throws Exception {
        repository = new LocalFeatureFileRepository(baseFolder.getRoot().getAbsolutePath());
    }

    @Test
    public void testLoadAll() throws Exception {
        final File folder = baseFolder.newFolder();
        final File first = new File(folder, "first-feature.yml");
        final File second = new File(folder, "second-feature.yml");
        BufferedWriter writer = Files.newWriter(first, Charset.defaultCharset());
        writer.write("active: true");
        writer.close();
        writer = Files.newWriter(second, Charset.defaultCharset());
        writer.write("active: false");
        writer.close();
        final ClientApplicationId applicationId = ClientApplicationId.of(folder.getName());
        StepVerifier
                .create(repository.loadAll())
                .assertNext(file -> {
                    assertThat(file).isNotNull();
                    assertThat(file.getId()).isEqualTo(new FeatureId(applicationId, "first-feature"));
                    assertThat(file.getBuffer().toString()).isEqualTo("active: true");
                })
                .assertNext(file -> {
                    assertThat(file).isNotNull();
                    assertThat(file.getId()).isEqualTo(new FeatureId(applicationId, "second-feature"));
                    assertThat(file.getBuffer().toString()).isEqualTo("active: false");
                })
                .verifyComplete();
    }

    @Test
    public void testNoMatchingFiles() throws Exception {
        final File folder = baseFolder.newFolder();
        final File other = new File(folder, "other-file.txt");
        BufferedWriter writer = Files.newWriter(other, Charset.defaultCharset());
        writer.write("nothing really important");
        writer.close();
        StepVerifier
                .create(repository.loadAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    public void testYmlAndYamlFilesBothPickedUpInFilenameOrder() throws Exception {
        final File folder = baseFolder.newFolder();
        final File first = new File(folder, "a.yml");
        final File second = new File(folder, "b.yaml");
        BufferedWriter writer = Files.newWriter(first, Charset.defaultCharset());
        writer.write("active: true");
        writer.close();
        writer = Files.newWriter(second, Charset.defaultCharset());
        writer.write("active: false");
        writer.close();
        final ClientApplicationId applicationId = ClientApplicationId.of(folder.getName());
        StepVerifier
                .create(repository.loadAll())
                .assertNext(file -> {
                    assertThat(file.getId()).isEqualTo(new FeatureId(applicationId, "a"));
                    assertThat(file.getBuffer().toString()).isEqualTo("active: true");
                })
                .assertNext(file -> {
                    assertThat(file.getId()).isEqualTo(new FeatureId(applicationId, "b"));
                    assertThat(file.getBuffer().toString()).isEqualTo("active: false");
                })
                .verifyComplete();
    }

    @Test
    public void testRootLevelFileIsNotAnAppDirectory() throws Exception {
        final File rootFile = baseFolder.newFile("root-note.txt");
        BufferedWriter writer = Files.newWriter(rootFile, Charset.defaultCharset());
        writer.write("just a file");
        writer.close();
        StepVerifier
                .create(repository.loadAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    public void testMissingRootPathYieldsError() throws Exception {
        final LocalFeatureFileRepository missing =
                new LocalFeatureFileRepository(new File(baseFolder.getRoot(), "does-not-exist").getAbsolutePath());
        StepVerifier
                .create(missing.loadAll())
                .expectError()
                .verify();
    }

}