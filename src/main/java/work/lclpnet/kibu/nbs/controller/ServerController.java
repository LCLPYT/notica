package work.lclpnet.kibu.nbs.controller;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.*;
import work.lclpnet.kibu.nbs.data.Song;
import work.lclpnet.kibu.nbs.impl.ServerBasicNotePlayer;
import work.lclpnet.kibu.nbs.impl.ServerPositionedNotePlayer;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A controller that executes commands on the server for the given {@link ServerPlayerEntity}.
 */
public class ServerController implements Controller, PlayerHolder {

    private ServerPlayerEntity player;
    private final SongResolver resolver;
    private final InstrumentSoundProvider soundProvider;
    private final Logger logger;
    private final Map<Identifier, PlayingSong> playing = new HashMap<>();

    public ServerController(ServerPlayerEntity player, SongResolver resolver, InstrumentSoundProvider soundProvider, Logger logger) {
        this.player = player;
        this.resolver = resolver;
        this.soundProvider = soundProvider;
        this.logger = logger;
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
        startSong(descriptor, () -> new ServerBasicNotePlayer(player, soundProvider, volume));
    }

    @Override
    public void playSongAt(SongDescriptor descriptor, Position position, float volume) {
        startSong(descriptor, () -> new ServerPositionedNotePlayer(player, position));
    }

    private <T extends NotePlayer & PlayerHolder> void startSong(SongDescriptor descriptor, Supplier<T> supplier) {
        resolver.resolve(descriptor)
                .exceptionally(error -> {
                    logger.error("Failed to resolve song {}", descriptor, error);
                    return null;
                })
                .thenAccept(song -> startSong(descriptor, song, supplier));

    }

    private <T extends NotePlayer & PlayerHolder> void startSong(SongDescriptor descriptor, @Nullable Song song, Supplier<T> supplier) {
        if (song == null) return;

        T notePlayer = supplier.get();
        SongPlayback playback = new SongPlayback(song, notePlayer);

        final Identifier id = descriptor.id();
        playback.whenDone(() -> removePlaying(id));

        synchronized (this) {
            playing.put(id, new PlayingSong(playback, notePlayer));
        }

        playback.start();
    }

    private void removePlaying(Identifier id) {
        synchronized (this) {
            playing.remove(id);
        }
    }

    private record PlayingSong(SongPlayback playback, PlayerHolder playerHolder) {}
}
