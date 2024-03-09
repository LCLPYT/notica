package work.lclpnet.kibu.nbs.api;

import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

import java.util.concurrent.CompletableFuture;

public interface SongResolver {

    CompletableFuture<Song> resolve(SongDescriptor descriptor);
}
