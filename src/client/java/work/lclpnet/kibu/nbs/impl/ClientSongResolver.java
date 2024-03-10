package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.api.SongResolver;
import work.lclpnet.kibu.nbs.util.PendingSong;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ClientSongResolver implements SongResolver {

    private Map<Identifier, PendingSong> songs = new HashMap<>();

    @Nullable
    public PendingSong resolve(SongDescriptor descriptor) {
        return songs.get(descriptor.id());
    }

    @Override
    public @Nullable PendingSong resolve(Identifier id) {
        return songs.get(id);
    }

    public void add(SongDescriptor descriptor, PendingSong pendingSong) {
        Objects.requireNonNull(pendingSong, "Song must not be null");
        songs.put(descriptor.id(), pendingSong);
    }
}
