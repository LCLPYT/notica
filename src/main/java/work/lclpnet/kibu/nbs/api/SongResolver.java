package work.lclpnet.kibu.nbs.api;

import work.lclpnet.kibu.nbs.data.Song;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SongResolver {

    CompletableFuture<Song> resolve(SongDescriptor descriptor);
}
