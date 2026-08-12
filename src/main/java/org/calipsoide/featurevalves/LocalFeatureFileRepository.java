package org.calipsoide.featurevalves;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.Comparator.comparing;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A {@link FeatureFileRepository} that reads feature definition files from the
 * local clone directory.
 * <p>
 * Each subdirectory of the data root is treated as a
 * {@link ClientApplicationId};
 * every {@code *.yml} / {@code *.yaml} file within it (sorted by file name)
 * becomes a {@link FeatureFile} whose id is derived from the folder and file
 * name.
 *
 * @see GitFeatureFileRepository
 */
@Repository
public class LocalFeatureFileRepository implements FeatureFileRepository {

    private static final int BUFFER_SIZE = 1024 * 20; // should be enough to load config files at once

    private Path path;

    /**
     * Creates the repository rooted at the given data path.
     *
     * @param path the root directory containing per-application folders of
     *             feature definition files
     */
    public LocalFeatureFileRepository(@Value("${features.git.local.data}") String path) {
        this.path = FileSystems.getDefault().getPath(path);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Non-directory entries at the root are ignored. Read failures are surfaced
     * as errors in the returned {@link Flux}.
     *
     * @return a {@link Flux} over one {@link FeatureFile} per definition file
     */
    @Override
    public Flux<FeatureFile> loadAll() {
        try {
            return Flux
                    .fromIterable(Files.newDirectoryStream(path))
                    .filter(Files::isDirectory)
                    .flatMap(path -> {
                        final String code = path.getFileName().toString();
                        final ClientApplicationId applicationId = ClientApplicationId.of(code);
                        return filesOf(applicationId);
                    });
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    /**
     * Loads the definition files for a single application, in file-name order.
     *
     * @param applicationId the application whose files should be read
     * @return a {@link Flux} over that application's {@link FeatureFile}s
     */
    private Flux<FeatureFile> filesOf(ClientApplicationId applicationId) {
        try {
            final Path folder = path.resolve(applicationId.toString());
            final ArrayList<Path> listing = new ArrayList<>();
            Files.newDirectoryStream(folder, "*.{yml,yaml}").forEach(listing::add);
            listing.sort(comparing(Path::getFileName));
            final Flux<Path> paths = Flux.fromIterable(listing).filter(Files::isRegularFile);
            return paths.flatMapSequential(path -> {
                final String filename = path.getFileName().toString();
                final String code = filename.replaceAll("(\\.yml|\\.yaml)$", "");
                final FeatureId id = new FeatureId(applicationId, code);
                return read(path).map(buffer -> new FeatureFile(id, buffer));
            });
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    /**
     * Decodes the given file into a {@link CharBuffer} using the platform
     * default charset.
     *
     * @param path the file to read
     * @return a {@code Mono} of the decoded content
     */
    private Mono<CharBuffer> read(Path path) {
        return DataBufferUtils
                .read(path, new DefaultDataBufferFactory(), BUFFER_SIZE)
                .map(buffer -> defaultCharset().decode(buffer.asByteBuffer()))
                .next();
    }

}
