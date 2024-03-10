package work.lclpnet.kibu.nbs.controller;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.api.*;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.ServerBasicNotePlayer;
import work.lclpnet.kibu.nbs.impl.ServerPositionedNotePlayer;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A controller that executes commands on the server for the given {@link ServerPlayerEntity}.
 */
public class ServerController implements Controller, PlayerHolder {

    private ServerPlayerEntity player;
    private final SongResolver resolver;
    private final InstrumentSoundProvider soundProvider;
    private final PlayerConfig playerConfig;
    private final Map<SongDescriptor, PlayingSong> playing = new HashMap<>();

    public ServerController(ServerPlayerEntity player, SongResolver resolver, InstrumentSoundProvider soundProvider,
                            PlayerConfig playerConfig) {
        this.player = player;
        this.resolver = resolver;
        this.soundProvider = soundProvider;
        this.playerConfig = playerConfig;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        synchronized (this) {
            this.player = player;

            for (PlayingSong playingSong : this.playing.values()) {
                playingSong.playerHolder.setPlayer(player);
            }
        }
    }

    @Override
    public void playSong(SongDescriptor descriptor, float volume) {
        Song song = resolver.resolve(descriptor);

        if (song == null) return;

        startSong(descriptor, song, () -> new ServerBasicNotePlayer(player, soundProvider, volume, playerConfig));
    }

    @Override
    public void playSongAt(SongDescriptor descriptor, Position position, float volume) {
        Song song = resolver.resolve(descriptor);

        if (song == null) return;

        startSong(descriptor, song, () -> new ServerPositionedNotePlayer(player, position));
    }

    @Override
    public void stopSong(SongDescriptor song) {
        PlayingSong playingSong = removePlaying(song);

        if (playingSong == null) return;

        playingSong.playback().stop();
    }

    @Override
    public Set<SongDescriptor> getPlayingSongs() {
        synchronized (this) {
            return new HashSet<>(playing.keySet());
        }
    }

    private <T extends NotePlayer & PlayerHolder> void startSong(SongDescriptor descriptor, Song song, Supplier<T> supplier) {
        stopSong(descriptor);

        T notePlayer = supplier.get();
        SongPlayback playback = new SongPlayback(song, notePlayer);

        playback.whenDone(() -> removePlaying(descriptor));

        synchronized (this) {
            playing.put(descriptor, new PlayingSong(playback, notePlayer));
        }

        playback.start();
    }

    @Nullable
    private PlayingSong removePlaying(SongDescriptor descriptor) {
        synchronized (this) {
            return playing.remove(descriptor);
        }
    }

    private record PlayingSong(SongPlayback playback, PlayerHolder playerHolder) {}
}
