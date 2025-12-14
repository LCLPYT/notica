package work.lclpnet.notica;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.api.PlaybackOptions;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.impl.NoticaImpl;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface Notica {

    /**
     * Play a song to a collection of players.
     * @param song The {@link CheckedSong} to play.
     * @param options The playback options, containing info for how to play the song.
     * @param startTick The tick to start the playback at.
     * @param players The song listeners.
     * @return A {@link SongHandle} that can be used to control the song playback.
     */
    SongHandle playSong(CheckedSong song, PlaybackOptions options, int startTick, Collection<? extends ServerPlayer> players);

    /**
     * Get all currently playing {@link SongHandle}s.
     * @return A set of all currently playing {@link SongHandle}s.
     */
    Set<SongHandle> getPlayingSongs();

    /**
     * Get all {@link SongHandle}s currently playing for a given player.
     * @param player The player.
     * @return A set of all {@link SongHandle}s the player is a listener of.
     */
    Set<SongHandle> getPlayingSongs(ServerPlayer player);

    /**
     * Get all {@link SongHandle} that play a song with the given songId.
     * @param songId The songId that identifies a song.
     * @return A set of all {@link SongHandle} that play the referenced song.
     */
    Set<SongHandle> getPlayingSongs(Identifier songId);

    /**
     * Get the {@link SongHandle} that plays a song, referenced by the given songId to a given player.
     * @param player The listening player.
     * @param songId The songId that identifies a song.
     * @return An optional {@link SongHandle}.
     */
    Optional<SongHandle> getPlayingSong(ServerPlayer player, Identifier songId);

    /**
     * Play a song to a collection of players.
     * @param song The {@link CheckedSong} to play.
     * @param volume The playback volume of the song, ranges [0, 1].
     * @param players The song listeners.
     * @return A {@link SongHandle} that can be used to control the song playback.
     */
    default SongHandle playSong(CheckedSong song, float volume, Collection<? extends ServerPlayer> players) {
        return playSong(song, volume, 0, players);
    }

    /**
     * Play a song to a collection of players.
     * @param song The {@link CheckedSong} to play.
     * @param volume The playback volume of the song, ranges [0, 1].
     * @param players The song listeners.
     * @return A {@link SongHandle} that can be used to control the song playback.
     */
    default SongHandle playSong(CheckedSong song, float volume, int startTick, Collection<? extends ServerPlayer> players) {
        return playSong(song, new PlaybackOptions(volume), startTick, players);
    }

    /**
     * Get the {@link Notica} instance for a given server.
     * @param server The Minecraft server.
     * @return The {@link Notica} instance for that server.
     */
    static Notica getInstance(MinecraftServer server) {
        return NoticaImpl.getInstance(server);
    }
}
