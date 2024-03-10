package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.api.SongResolver;
import work.lclpnet.kibu.nbs.api.data.Song;

import java.util.HashMap;
import java.util.Map;

public class SimpleSongResolver implements SongResolver {

    private final Map<Identifier, Song> songs = new HashMap<>();

    @Override
    public Song resolve(SongDescriptor descriptor) {
        Identifier id = descriptor.id();
        return songs.get(id);
    }

    @Override
    public @Nullable Song resolve(Identifier id) {
        return songs.get(id);
    }

    public void addSong(Identifier id, Song song) {
        songs.put(id, song);
    }
}
