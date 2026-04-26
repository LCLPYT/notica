package work.lclpnet.notica.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.api.Speaker;

import java.util.Set;

/**
 * Handles starting and stopping of songs for positional songs in the world and global songs.
 */
public class SongPlaybackListener {

    public static final double POSITIONAL_SOUND_TRACKING_PROXIMITY = 48;

    private final ActiveSongManager activeSongManager;

    public SongPlaybackListener(ActiveSongManager activeSongManager) {
        this.activeSongManager = activeSongManager;
    }

    public void init() {
        PlayerConnectionHooks.JOIN.register(this::onPlayerJoin);

        EntityTrackingEvents.START_TRACKING.register(this::onStartTrackingEntity);
        EntityTrackingEvents.STOP_TRACKING.register(this::onStopTrackingEntity);

        ServerTickEvents.END_WORLD_TICK.register(this::onLevelTick);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(this::onLevelChange);
    }

    private void onPlayerJoin(ServerPlayer player) {
        for (SongHandle handle : activeSongManager.getGlobalHandles()) {
            handle.add(player);
        }
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

    private void onLevelTick(ServerLevel level) {
        var players = PlayerLookup.world(level);
        var handles = activeSongManager.getPositionedHandles(level);

        for (SongHandle handle : handles) {
            Speaker speaker = handle.getSpeaker();

            if (speaker == null) continue;

            for (ServerPlayer player : players) {
                if (speaker.isWithinRange(player.position(), POSITIONAL_SOUND_TRACKING_PROXIMITY)) {
                    handle.add(player);
                } else {
                    handle.remove(player);
                }
            }
        }
    }

    private void onLevelChange(ServerPlayer player, ServerLevel from, ServerLevel to) {
        Set<SongHandle> handles = activeSongManager.getPositionedHandles(from);

        for (SongHandle handle : handles) {
            handle.remove(player);
        }
    }
}
