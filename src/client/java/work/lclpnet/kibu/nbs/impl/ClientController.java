package work.lclpnet.kibu.nbs.impl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.api.SongPlayback;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.controller.Controller;
import work.lclpnet.kibu.nbs.network.packet.StopSongBidiPacket;
import work.lclpnet.kibu.nbs.util.PlayerConfigEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClientController implements Controller {

    private final ClientSongResolver songResolver;
    private final Logger logger;
    private final InstrumentSoundProvider soundProvider;
    private final PlayerConfigEntry playerConfig;
    private final Map<SongDescriptor, SongPlayback> playing = new HashMap<>();

    public ClientController(ClientSongResolver songResolver, Logger logger, InstrumentSoundProvider soundProvider,
                            PlayerConfigEntry playerConfig) {
        this.songResolver = songResolver;
        this.logger = logger;
        this.soundProvider = soundProvider;
        this.playerConfig = playerConfig;
    }

    @Override
    public void playSong(SongDescriptor descriptor, float volume) {
        Song song = songResolver.resolve(descriptor);

        if (song == null) {
            logger.error("Unknown song {}", descriptor);
            return;
        }

        stopSong(descriptor);

        NotePlayer notePlayer = new ClientBasicNotePlayer(soundProvider, volume, playerConfig);
        SongPlayback playback = new SongPlayback(song, notePlayer);

        playback.whenDone(() -> {
            removePlaying(descriptor);
            notifySongStopped(descriptor);
        });

        synchronized (this) {
            playing.put(descriptor, playback);
        }

        playback.start();
    }

    @Override
    public void playSongAt(SongDescriptor song, Position position, float volume) {

    }

    @Override
    public void stopSong(SongDescriptor song) {
        SongPlayback playback = removePlaying(song);

        if (playback == null) return;

        playback.stop();
    }

    public void stopSong(Identifier songId) {
        playing.keySet().stream()
                .filter(d -> d.id().equals(songId))
                .findAny()
                .ifPresent(this::stopSong);
    }

    @Nullable
    private SongPlayback removePlaying(SongDescriptor descriptor) {
        synchronized (this) {
            return playing.remove(descriptor);
        }
    }

    private void notifySongStopped(SongDescriptor descriptor) {
        if (!ClientPlayNetworking.canSend(StopSongBidiPacket.TYPE)) return;

        var packet = new StopSongBidiPacket(descriptor.id());
        ClientPlayNetworking.send(packet);
    }

    @Override
    public Set<SongDescriptor> getPlayingSongs() {
        return playing.keySet();
    }
}
