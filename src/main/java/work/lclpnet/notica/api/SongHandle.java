package work.lclpnet.notica.api;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.api.data.Song;

import java.util.Set;

/**
 * A handle for a playing song instance.
 * The song can be played to multiple players at once.
 */
public interface SongHandle {

    Identifier getSongId();

    Song getSong();

    /**
     * Stops the song for all listeners of this song instance.
     */
    void stop();

    Set<ServerPlayerEntity> getListeners();

    boolean isListener(ServerPlayerEntity player);

    /**
     * Remove a single player from the listeners list of this song instance.
     * @param player The player to remove.
     */
    void remove(ServerPlayerEntity player);

    void onDestroy(Runnable action);

    /**
     * Sets the playback position in ticks.
     * @param ticks The playback position (time), in ticks.
     * @param absolute Whether the playback position is absolute or relative.
     */
    void seekTo(int ticks, boolean absolute);
}
