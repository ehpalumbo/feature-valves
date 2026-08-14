package org.calipsoide.featurevalves.infra.git;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.Comparator.comparing;

import java.nio.CharBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.calipsoide.featurevalves.application.FeatureFile;
import org.calipsoide.featurevalves.application.FeatureFileRepository;
import org.calipsoide.featurevalves.domain.ClientApplicationId;
import org.calipsoide.featurevalves.domain.FeatureId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * A {@link FeatureFileRepository} that reads feature definition files from the
 * local clone directory.
 * <p>
 * Each subdirectory of the data root is treated as a
 * {@link ClientApplicationId};
 * every {@code *.yml} / {@code *.yaml} file within it (sorted by file name)
 * becomes a {@link FeatureFile} whose id is derived from the folder and file
 * name.
 * <p>
 * Blocking filesystem enumeration is offloaded to a bounded-elastic
 * {@link Scheduler}, while file content is read with the reactive
 * {@link DataBufferUtils#read(Path, org.springframework.core.io.buffer.DataBufferFactory, int)}
 * overload. Each {@link DirectoryStream} is opened via {@link Flux#using},
 * which closes it on completion, error, or cancellation.
 *
 * @see GitFeatureFileRepository
 */
@Repository
public class LocalFeatureFileRepository implements FeatureFileRepository {

    private static final int BUFFER_SIZE = 1024 * 20; // should be enough to load config files at once

    private final Path path;

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
     * as errors in the returned {@link Flux}. The whole stream is subscribed on
     * a bounded-elastic {@link Scheduler}, so the blocking filesystem scans run
     * off the reactive pipeline threads.
     *
     * @return a {@link Flux} over one {@link FeatureFile} per definition file
     */
    @Override
    public Flux<FeatureFile> loadAll() {
        return listApplicationIds()
                .flatMapSequential(this::loadFeatureFiles)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Lists the {@link ClientApplicationId}s of the root directories. The
     * {@link DirectoryStream} is closed when the returned {@link Flux}
     * terminates, errors, or is cancelled.
     *
     * @return a {@link Flux} over one {@link ClientApplicationId} per directory
     */
    private Flux<ClientApplicationId> listApplicationIds() {
        return Flux.using(
                () -> Files.newDirectoryStream(path),
                stream -> Flux.fromIterable(stream)
                        .filter(Files::isDirectory)
                        .map(entry -> ClientApplicationId.of(entry.getFileName().toString())));
    }

    /**
     * Loads the definition files for a single application, in file-name order.
     *
     * @param applicationId the application whose files should be read
     * @return a {@link Flux} over that application's {@link FeatureFile}s
     */
    private Flux<FeatureFile> loadFeatureFiles(ClientApplicationId applicationId) {
        final Path folder = path.resolve(applicationId.toString());
        return listFeatureFiles(folder).flatMapSequential(file -> {
            final String filename = file.getFileName().toString();
            final String code = filename.replaceAll("(\\.yml|\\.yaml)$", "");
            final FeatureId id = new FeatureId(applicationId, code);
            return read(file).map(buffer -> new FeatureFile(id, buffer));
        });
    }

    /**
     * Lists the {@code *.yml} / {@code *.yaml} regular files of a folder in
     * file-name order. The {@link DirectoryStream} is closed when the returned
     * {@link Flux} terminates, errors, or is cancelled.
     *
     * @param folder the folder to scan
     * @return a {@link Flux} over the matching files, sorted by file name
     */
    private Flux<Path> listFeatureFiles(Path folder) {
        return Flux.using(
                () -> Files.newDirectoryStream(folder, "*.{yml,yaml}"),
                stream -> Flux.fromIterable(stream)
                        .filter(Files::isRegularFile)
                        .collectList()
                        .doOnNext(files -> files.sort(comparing(Path::getFileName)))
                        .flatMapMany(Flux::fromIterable));
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
