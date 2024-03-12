package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.util.PendingSong;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ClientSongRepository {

    private final Map<CachedSong, PendingSong> cachedSongs = new HashMap<>();
    private final Map<Identifier, PendingSong> songsById = new HashMap<>();

    @Nullable
    public PendingSong get(byte[] checksum) {
        return cachedSongs.get(new CachedSong(checksum));
    }

    @Nullable
    public PendingSong get(Identifier id) {
        return songsById.get(id);
    }

    public void add(Identifier id, byte[] checksum, PendingSong pendingSong) {
        Objects.requireNonNull(pendingSong, "Song must not be null");
        cachedSongs.put(new CachedSong(checksum), pendingSong);
        songsById.put(id, pendingSong);
    }

    private record CachedSong(byte[] checksum) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CachedSong that = (CachedSong) o;
            return Arrays.equals(checksum, that.checksum);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(checksum);
        }
    }
}
