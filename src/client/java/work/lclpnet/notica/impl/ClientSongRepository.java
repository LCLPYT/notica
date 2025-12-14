package work.lclpnet.notica.impl;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ClientSongRepository {

    private final Map<Checksum, PendingSong> byChecksum = new HashMap<>();
    private final Map<ResourceLocation, Checksum> byId = new HashMap<>();
    private final Map<Checksum, List<ResourceLocation>> references = new HashMap<>();

    @Nullable
    public synchronized PendingSong get(byte[] checksum) {
        return byChecksum.get(new Checksum(checksum));
    }

    @Nullable
    public synchronized PendingSong get(ResourceLocation id) {
        Checksum checksum = byId.get(id);

        if (checksum == null) return null;

        return byChecksum.get(checksum);
    }

    public synchronized void add(PendingSong pendingSong) {
        Objects.requireNonNull(pendingSong, "Song must not be null");
        var key = new Checksum(pendingSong.checksum());
        byChecksum.put(key, pendingSong);
    }

    public synchronized void bind(PendingSong song, ResourceLocation id) {
        var key = new Checksum(song.checksum());

        if (!byChecksum.containsKey(key)) {
            throw new IllegalArgumentException("Song isn't added to the repository");
        }

        byId.put(id, key);

        var boundIds = references.computeIfAbsent(key, _key -> new ArrayList<>(1));

        boundIds.add(id);
    }

    public synchronized void unbind(PendingSong song, ResourceLocation id) {
        var key = new Checksum(song.checksum());

        List<ResourceLocation> boundIds = references.get(key);

        if (boundIds == null || !boundIds.remove(id)) return;

        if (!boundIds.contains(id)) {
            byId.remove(id);
        }

        if (!boundIds.isEmpty()) return;

        byChecksum.remove(key);
        references.remove(key);
    }

    private record Checksum(byte[] checksum) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Checksum that = (Checksum) o;
            return Arrays.equals(checksum, that.checksum);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(checksum);
        }
    }
}
