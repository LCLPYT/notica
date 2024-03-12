package work.lclpnet.kibu.nbs;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.api.CheckedSong;
import work.lclpnet.kibu.nbs.api.SongHandle;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface KibuNbsApi {

    /**
     * Play a song to a collection of players.
     * @param song The {@link CheckedSong} to play.
     * @param volume The playback volume of the song, ranges [0, 1].
     * @param players The song listeners.
     * @return A {@link SongHandle} that can be used to control the song playback.
     */
    SongHandle playSong(CheckedSong song, float volume, Collection<? extends ServerPlayerEntity> players);

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
    Set<SongHandle> getPlayingSongs(ServerPlayerEntity player);

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
    Optional<SongHandle> getPlayingSong(ServerPlayerEntity player, Identifier songId);

    /**
     * Get the {@link KibuNbsApi} instance for a given server.
     * @param server The Minecraft server.
     * @return The {@link KibuNbsApi} instance for that server.
     */
    static KibuNbsApi getInstance(MinecraftServer server) {
        return KibuNbsApiImpl.getInstance(server);
    }
}
