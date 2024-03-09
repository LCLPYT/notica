package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.api.SongResolver;
import work.lclpnet.kibu.nbs.api.data.Song;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SimpleSongResolver implements SongResolver {

    private final Map<Identifier, Song> songs = new HashMap<>();

    @Override
    public CompletableFuture<Song> resolve(SongDescriptor descriptor) {
        Identifier id = descriptor.id();
        Song song = songs.get(id);
        return CompletableFuture.completedFuture(song);
    }

    public void addSong(Identifier id, Song song) {
        songs.put(id, song);
    }
}
