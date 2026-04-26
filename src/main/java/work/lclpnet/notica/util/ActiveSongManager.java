package work.lclpnet.notica.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.api.Speaker;
import work.lclpnet.notica.impl.ServerSongHandle;

import java.util.*;

/**
 * Tracks and manages active song handles.
 */
public class ActiveSongManager {

    /**
     * Speaker source entity uuid -> [SongHandle]
     */
    private final Map<UUID, Set<SongHandle>> handlesByEntity = new HashMap<>();
    private final Map<SongHandle, UUID> entityHandles = new HashMap<>();
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

        removeLookup(entityHandles, handlesByEntity, handle);
        removeLookup(positionedHandles, handlesByChunk, handle);
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
        entityHandles.put(handle, uuid);

        handlesByEntity.computeIfAbsent(uuid, u -> new HashSet<>()).add(handle);
    }

    private <T> void removeLookup(Map<SongHandle, T> lookup, Map<T, Set<SongHandle>> inverseLookup, SongHandle handle) {
        @Nullable T value = lookup.remove(handle);

        if (value == null) return;

        Set<SongHandle> handles = inverseLookup.get(value);

        if (handles == null) return;

        handles.remove(handle);

        if (handles.isEmpty()) {
            inverseLookup.remove(value);
        }
    }

    public synchronized Set<SongHandle> getAllHandles() {
        return Collections.unmodifiableSet(allHandles);
    }

    public synchronized Set<SongHandle> getGlobalHandles() {
        return Collections.unmodifiableSet(globalHandles);
    }

    public synchronized Set<SongHandle> getSongBySpeakerEntityUuid(UUID uuid) {
        return handlesByEntity.getOrDefault(uuid, Set.of());
    }
}
