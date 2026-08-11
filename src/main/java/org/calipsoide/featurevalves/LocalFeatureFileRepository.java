package org.calipsoide.featurevalves;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.nio.channels.AsynchronousFileChannel.open;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Comparator.comparing;

/**
 * Created by epalumbo on 9/17/17.
 */
@Repository
public class LocalFeatureFileRepository implements FeatureFileRepository {

    private static final int BUFFER_SIZE = 1024 * 20; // should be enough to load config files at once

    private Path path;

    public LocalFeatureFileRepository(@Value("${features.git.local.data}") String path) {
        this.path = FileSystems.getDefault().getPath(path);
    }

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

    private Mono<CharBuffer> read(Path path) {
        try {
            final AsynchronousFileChannel channel = open(path);
            return DataBufferUtils
                    .read(channel, new DefaultDataBufferFactory(), BUFFER_SIZE)
                    .map(buffer -> defaultCharset().decode(buffer.asByteBuffer()))
                    .next();
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

}
