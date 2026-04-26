package work.lclpnet.notica.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
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

    Set<ServerPlayer> getListeners();

    boolean isListener(ServerPlayer player);

    /**
     * Adds a single player to the listeners list of this song instance.
     * The song is not exactly synchronized between all the listeners, especially if a listener was added later.
     * Some listeners may also have doppler-effects active which further desynchronizes playback positions.
     * Therefore, implementations cannot perfectly synchronize songs between all listeners.
     * @param player The player to add.
     */
    void add(ServerPlayer player);

    /**
     * Remove a single player from the listeners list of this song instance.
     * @param player The player to remove.
     */
    void remove(ServerPlayer player);

    void onDestroy(Runnable action);

    /**
     * Sets the playback position in ticks.
     * @param ticks The playback position (time), in ticks.
     * @param absolute Whether the playback position is absolute or relative.
     */
    void seekTo(int ticks, boolean absolute);

    /**
     * Gets the speaker of this song handle, if there is any.
     * @return The speaker of this song.
     */
    @Nullable
    Speaker getSpeaker();
}
