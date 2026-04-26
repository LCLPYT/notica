package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.network.SongHeader;
import work.lclpnet.notica.network.SongPlayOptions;
import work.lclpnet.notica.network.SongSlicer;
import work.lclpnet.notica.network.packet.PlaySongS2CPacket;
import work.lclpnet.notica.network.packet.SongSeekS2CPacket;
import work.lclpnet.notica.network.packet.StopSongBidiPacket;

import java.util.*;

public class ServerSongHandle implements SongHandle, PlayerStoppedPlaybackListener {

    private final CheckedSong checkedSong;
    private final PlaybackOptions playbackOptions;
    private final int startTick;
    private final @Nullable Speaker speaker;
    private final SongPlayerRefFactory refFactory;
    private final boolean global;
    private final Map<UUID, SongPlayerRef> vanillaRefs = new HashMap<>(), moddedRefs = new HashMap<>();
    private volatile boolean started = false;
    @Nullable
    private IndividualSongPlayback serverPlayback = null;
    @Nullable
    private ServerBasicNotePlayer serverNotePlayer = null;
    private final Hook<Runnable> onDestroy = HookFactory.createArrayBacked(Runnable.class, callbacks -> () -> {
        for (Runnable callback : callbacks) {
            callback.run();
        }
    });
    private boolean destroyed = false;

    public ServerSongHandle(CheckedSong checkedSong, PlaybackOptions playbackOptions, int startTick, @Nullable Speaker speaker, SongPlayerRefFactory refFactory, boolean global) {
        this.checkedSong = checkedSong;
        this.playbackOptions = playbackOptions;
        this.startTick = startTick;
        this.speaker = speaker;
        this.refFactory = refFactory;
        this.global = global;
    }

    public synchronized void start(Collection<? extends ServerPlayer> players, InstrumentSoundProvider soundProvider) {
        if (started) return;
        started = true;

        Set<SongPlayerRef> vanillaPlayers = new HashSet<>();
        Set<SongPlayerRef> moddedPlayers = new HashSet<>();

        for (ServerPlayer player : players) {
            SongPlayerRef ref = refFactory.createRef(player);

            if (NoticaImpl.hasModInstalled(player)) {
                moddedPlayers.add(ref);
            } else {
                vanillaPlayers.add(ref);
            }
        }

        this.moddedRefs.clear();

        for (SongPlayerRef playerRef : moddedPlayers) {
            ServerPlayer player = playerRef.getPlayer();
            sendPlayPacket(player, startTick);
            this.moddedRefs.put(player.getUUID(), playerRef);
        }

        this.vanillaRefs.clear();

        for (SongPlayerRef playerRef : vanillaPlayers) {
            UUID uuid = playerRef.getPlayer().getUUID();
            this.vanillaRefs.put(uuid, playerRef);
        }

        SoundPositionProvider soundPositionProvider = switch (playbackOptions.channelMode()) {
            case MONO -> speaker != null
                    ? SoundPositionProvider.ofSpeaker(speaker.asMonoSpeaker())
                    : SoundPositionProvider.worldPlayerMono();
            case STEREO -> speaker != null
                    ? SoundPositionProvider.ofSpeaker(speaker)
                    : SoundPositionProvider.worldPlayerRelative();
        };

        serverNotePlayer = new ServerBasicNotePlayer(
                vanillaPlayers,
                soundProvider,
                playbackOptions.volume(),
                soundPositionProvider
        );

        final IndividualSongPlayback playback = createServerPlayback();

        serverPlayback = playback;
        playback.start(startTick);
    }

    private @NotNull IndividualSongPlayback createServerPlayback() {
        final IndividualSongPlayback playback = new IndividualSongPlayback(
                checkedSong.song(),
                serverNotePlayer,
                playbackOptions.loopOverride()
        );

        playback.whenDone(() -> {
            this.vanillaRefs.clear();

            checkDestroyed();

            if (destroyed) return;

            // modded clients remain; normally clients should send a stopped packet,
            // but in case clients misbehave, force stop the song after a timeout
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ignored) {}

                stop();
            });
        });

        return playback;
    }

    private void sendPlayPacket(ServerPlayer player, int tick) {
        Song song = checkedSong.song();
        SongHeader header = new SongHeader(song);

        // send the first 5 seconds along with the play packet, so that the client can start playing instantly
        SongSlice slice = SongSlicer.sliceSeconds(song, tick, 5);
        boolean finished = SongSlicer.isFinished(song, slice);

        var options = new SongPlayOptions(checkedSong.id(), playbackOptions, tick, Optional.ofNullable(speaker));
        var packet = new PlaySongS2CPacket(options, header, slice, finished, checkedSong.checksum());
        ServerPlayNetworking.send(player, packet);
    }

    private void sendStopPacket(ServerPlayer player) {
        var packet = new StopSongBidiPacket(checkedSong.id());
        ServerPlayNetworking.send(player, packet);
    }

    private void sendSeekPacket(ServerPlayer player, int ticks, boolean absolute) {
        var packet = new SongSeekS2CPacket(checkedSong.id(), ticks, absolute);
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public Identifier getSongId() {
        return checkedSong.id();
    }

    @Override
    public Song getSong() {
        return checkedSong.song();
    }

    @Override
    public synchronized void stop() {
        if (!started) return;

        for (SongPlayerRef playerRef : moddedRefs.values()) {
            sendStopPacket(playerRef.getPlayer());
        }

        moddedRefs.clear();

        if (serverPlayback != null) {
            serverPlayback.stop();
            serverPlayback = null;
        }

        vanillaRefs.clear();

        serverNotePlayer = null;

        destroy();
    }

    private void destroy() {
        synchronized (this) {
            if (destroyed) return;

            destroyed = true;
        }

        onDestroy.invoker().run();
    }

    @Override
    public synchronized Set<ServerPlayer> getListeners() {
        Set<ServerPlayer> listeners = new HashSet<>();

        for (SongPlayerRef playerRef : moddedRefs.values()) {
            listeners.add(playerRef.getPlayer());
        }

        for (SongPlayerRef playerRef : vanillaRefs.values()) {
            listeners.add(playerRef.getPlayer());
        }

        return listeners;
    }

    @Override
    public synchronized boolean isListener(ServerPlayer player) {
        UUID uuid = player.getUUID();

        return moddedRefs.containsKey(uuid) || vanillaRefs.containsKey(uuid);
    }

    @Override
    public synchronized void add(ServerPlayer player) {
        if (!started || destroyed) return;

        UUID uuid = player.getUUID();

        if (moddedRefs.containsKey(uuid) || vanillaRefs.containsKey(uuid)) return;

        SongPlayerRef ref = refFactory.createRef(player);

        IndividualSongPlayback serverPlayback = this.serverPlayback;
        int currentTick = serverPlayback != null ? serverPlayback.getCurrentTick() : startTick;

        if (NoticaImpl.hasModInstalled(player)) {
            sendPlayPacket(player, currentTick);

            moddedRefs.put(uuid, ref);
        } else {
            vanillaRefs.put(uuid, ref);

            ServerBasicNotePlayer serverNotePlayer = this.serverNotePlayer;

            if (serverNotePlayer != null) {
                serverNotePlayer.addPlayer(ref);
            }
        }
    }

    @Override
    public synchronized void remove(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (moddedRefs.remove(uuid) != null) {
            sendStopPacket(player);
            checkDestroyed();
            return;
        }

        SongPlayerRef playerRef = vanillaRefs.remove(uuid);

        if (playerRef == null) return;

        // player was added to the server players

        if (serverNotePlayer != null) {
            serverNotePlayer.removePlayer(playerRef);
        }

        checkDestroyed();
    }

    @Override
    public synchronized void onDestroy(Runnable action) {
        onDestroy.register(action);
    }

    @Override
    public synchronized void onStoppedPlayback(ServerPlayer player) {
        moddedRefs.remove(player.getUUID());

        checkDestroyed();
    }

    private void checkDestroyed() {
        if (global || !moddedRefs.isEmpty() || !vanillaRefs.isEmpty()) return;

        if (serverPlayback != null) {
            serverPlayback.stop();
            serverPlayback = null;
            serverNotePlayer = null;
        }

        destroy();
    }

    @Override
    public String toString() {
        return "ServerSongHandle{checkedSong=%s, volume=%s, vanillaPlayers=%s, moddedPlayers=%s, started=%s}"
                .formatted(checkedSong, playbackOptions, vanillaRefs, moddedRefs, started);
    }

    @Override
    public synchronized void seekTo(int ticks, boolean absolute) {
        // if there are vanilla listeners, seek for them using serverPlayback

        if (serverPlayback != null) {
            serverPlayback.seekTo(ticks, absolute);
        }

        // all modded players receive a seek packet

        for (SongPlayerRef ref : moddedRefs.values()) {
            sendSeekPacket(ref.getPlayer(), ticks, absolute);
        }
    }

    @Override
    public @Nullable Speaker getSpeaker() {
        return speaker;
    }

    @Override
    public boolean isGlobal() {
        return global;
    }

    public interface SongPlayerRefFactory {
        SongPlayerRef createRef(ServerPlayer player);
    }
}
