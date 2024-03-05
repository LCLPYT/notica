package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.api.SongDecoder;
import work.lclpnet.kibu.nbs.data.Song;
import work.lclpnet.kibu.nbs.api.SongResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class DirectorySongResolver implements SongResolver {

    private final Path songsDirectory;
    private final Executor executor;

    public DirectorySongResolver(Path songsDirectory, Executor executor) {
        this.songsDirectory = songsDirectory;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Song> resolve(SongDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return resolveSync(descriptor);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private Song resolveSync(SongDescriptor descriptor) throws IOException {
        Identifier id = descriptor.id();

        Path path = songsDirectory;
        String namespace = id.getNamespace();

        if (!namespace.equals("minecraft")) {
            path = path.resolve(namespace);
        }

        path = path.resolve(id.getPath() + ".nbs");

        try (var in = Files.newInputStream(path)) {
            return SongDecoder.parse(in);
        }
    }
}
