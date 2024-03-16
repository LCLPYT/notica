package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.NotePlayer;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.network.packet.StopSongBidiPacket;
import work.lclpnet.notica.util.PlayerConfigEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientMusicBackend {

    private final ClientSongRepository songRepository;
    private final Logger logger;
    private final InstrumentSoundProvider soundProvider;
    private final PlayerConfigEntry playerConfig;
    private final Map<Identifier, SongPlayback> playing = new HashMap<>();

    public ClientMusicBackend(ClientSongRepository songRepository, Logger logger, InstrumentSoundProvider soundProvider,
                              PlayerConfigEntry playerConfig) {
        this.songRepository = songRepository;
        this.logger = logger;
        this.soundProvider = soundProvider;
        this.playerConfig = playerConfig;
    }

    public void playSong(Identifier songId, float volume, int startTick) {
        Song song = songRepository.get(songId);

        if (song == null) {
            logger.error("Unknown song {}", songId);
            return;
        }

        stopSong(songId);

        NotePlayer notePlayer = new ClientBasicNotePlayer(soundProvider, volume, playerConfig);
        SongPlayback playback = new SongPlayback(song, notePlayer);

        playback.whenDone(() -> {
            if (playback.isStopped()) return;

            removePlaying(songId);
            notifySongStopped(songId);
        });

        synchronized (this) {
            playing.put(songId, playback);
        }

        playback.start(startTick);
    }

    public void stopSong(Identifier songId) {
        SongPlayback playback = removePlaying(songId);

        if (playback == null) return;

        playback.stop();
    }

    @Nullable
    private SongPlayback removePlaying(Identifier songId) {
        synchronized (this) {
            return playing.remove(songId);
        }
    }

    private void notifySongStopped(Identifier songId) {
        if (!ClientPlayNetworking.canSend(StopSongBidiPacket.TYPE)) return;

        var packet = new StopSongBidiPacket(songId);
        ClientPlayNetworking.send(packet);
    }

    public Set<Identifier> getPlayingSongs() {
        return new HashSet<>(playing.keySet());
    }

    public void stopAll() {
        for (Identifier songId : getPlayingSongs()) {
            stopSong(songId);
        }
    }
}
