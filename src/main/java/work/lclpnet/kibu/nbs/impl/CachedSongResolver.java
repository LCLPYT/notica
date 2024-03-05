package work.lclpnet.kibu.nbs.impl;

import work.lclpnet.kibu.nbs.data.Song;
import work.lclpnet.kibu.nbs.api.SongResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CachedSongResolver implements SongResolver {

    private final SongResolver parent;
    private final Map<SongDescriptor, Song> cache = new HashMap<>();

    public CachedSongResolver(SongResolver parent) {
        this.parent = parent;
    }

    @Override
    public CompletableFuture<Song> resolve(SongDescriptor descriptor) {
        Song cached = cache.get(descriptor);

        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        var future = parent.resolve(descriptor);

        future.thenAccept(song -> {
            if (song != null) {
                cache.put(descriptor, song);
            }
        });

        return future;
    }
}
