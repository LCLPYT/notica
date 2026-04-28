package work.lclpnet.notica.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
    private final Map<ResourceKey<Level>, Set<SongHandle>> handlesByLevel = new HashMap<>();
    private final Map<SongHandle, ResourceKey<Level>> levelHandles = new HashMap<>();
    private final Set<SongHandle> globalHandles = new HashSet<>();
    private final Set<SongHandle> allHandles = new HashSet<>();

    public synchronized void removeHandle(SongHandle handle) {
        allHandles.remove(handle);
        globalHandles.remove(handle);

        removeLookup(entityHandles, handlesByEntity, handle);
        removeLookup(levelHandles, handlesByLevel, handle);
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
                () -> addPositioned(handle, speaker.dimension())
        );
    }

    private void addPositioned(ServerSongHandle handle, ResourceKey<Level> dimension) {
        levelHandles.put(handle, dimension);

        handlesByLevel.computeIfAbsent(dimension, p -> new HashSet<>()).add(handle);
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

    public synchronized Set<SongHandle> getPositionedHandles(ServerLevel level) {
        return handlesByLevel.getOrDefault(level.dimension(), Set.of());
    }
}
