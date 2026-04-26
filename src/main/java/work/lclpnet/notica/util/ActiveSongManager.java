package work.lclpnet.notica.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.api.Speaker;
import work.lclpnet.notica.impl.ServerSongHandle;

import java.util.*;

public class ActiveSongManager {

    /**
     * Speaker source entity uuid -> SongHandle
     */
    private final BiMap<UUID, SongHandle> entityHandles = HashBiMap.create();
    /**
     * Speaker source chunk pos -> [SongHandle]
     */
    private final Map<ChunkPos, Set<SongHandle>> handlesByChunk = new HashMap<>();
    private final Map<SongHandle, ChunkPos> positionedHandles = new HashMap<>();
    private final Set<SongHandle> globalHandles = new HashSet<>();
    private final Set<SongHandle> allHandles = new HashSet<>();

    public synchronized void removeHandle(SongHandle handle) {
        allHandles.remove(handle);
        globalHandles.remove(handle);
        entityHandles.inverse().remove(handle);

        removePositionedHandle(handle);
    }

    public synchronized void addGlobal(ServerSongHandle handle) {
        globalHandles.add(handle);

        add(handle);
    }

    public synchronized void add(ServerSongHandle handle) {
        allHandles.add(handle);

        Speaker speaker = handle.getSpeaker();

        if (speaker == null) return;

        speaker.sourceEntityUuid().ifPresentOrElse(
                uuid -> addEntity(handle, uuid),
                () -> addPositioned(handle, speaker.position())
        );
    }

    private void addPositioned(ServerSongHandle handle, Vec3 pos) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));

        positionedHandles.put(handle, chunk);

        handlesByChunk.computeIfAbsent(chunk, p -> new HashSet<>()).add(handle);
    }

    private void addEntity(ServerSongHandle handle, UUID uuid) {
        entityHandles.put(uuid, handle);
    }

    private void removePositionedHandle(SongHandle handle) {
        @Nullable ChunkPos chunkPos = positionedHandles.remove(handle);

        if (chunkPos == null) return;

        Set<SongHandle> handles = handlesByChunk.get(chunkPos);

        if (handles == null) return;

        handles.remove(handle);

        if (handles.isEmpty()) {
            handlesByChunk.remove(chunkPos);
        }
    }

    public Set<SongHandle> getAllHandles() {
        return Collections.unmodifiableSet(allHandles);
    }
}
