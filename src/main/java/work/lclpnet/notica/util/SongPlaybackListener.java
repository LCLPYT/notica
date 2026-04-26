package work.lclpnet.notica.util;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.notica.api.SongHandle;

import java.util.Set;

/**
 * Handles starting and stopping of songs for positional songs in the world and global songs.
 */
public class SongPlaybackListener {

    private final ActiveSongManager activeSongManager;

    public SongPlaybackListener(ActiveSongManager activeSongManager) {
        this.activeSongManager = activeSongManager;
    }

    public void init() {
        PlayerConnectionHooks.JOIN.register(this::onPlayerJoin);

        EntityTrackingEvents.START_TRACKING.register(this::onStartTrackingEntity);
        EntityTrackingEvents.STOP_TRACKING.register(this::onStopTrackingEntity);
    }

    private void onPlayerJoin(ServerPlayer player) {
        for (SongHandle handle : activeSongManager.getGlobalHandles()) {
            handle.add(player);
        }
//        player.level().getChunkSource().chunkMap.isChunkTracked(player, )
//        player.getChunkTrackingView()
    }

    private void onStartTrackingEntity(Entity entity, ServerPlayer player) {
        Set<SongHandle> handles = activeSongManager.getSongBySpeakerEntityUuid(entity.getUUID());

        for (SongHandle handle : handles) {
            handle.add(player);
        }
    }

    private void onStopTrackingEntity(Entity entity, ServerPlayer player) {
        Set<SongHandle> handles = activeSongManager.getSongBySpeakerEntityUuid(entity.getUUID());

        for (SongHandle handle : handles) {
            handle.remove(player);
        }
    }
}
