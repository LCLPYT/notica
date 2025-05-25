package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
    private final float volume;
    private final int startTick;
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

    public ServerSongHandle(CheckedSong checkedSong, float volume, int startTick) {
        this.checkedSong = checkedSong;
        this.volume = volume;
        this.startTick = startTick;
    }

    public synchronized void start(Set<SongPlayerRef> vanillaPlayers, Set<SongPlayerRef> moddedPlayers, InstrumentSoundProvider soundProvider) {
        if (started) return;
        started = true;

        this.moddedRefs.clear();

        for (SongPlayerRef playerRef : moddedPlayers) {
            ServerPlayerEntity player = playerRef.getPlayer();
            sendPlayPacket(player);
            this.moddedRefs.put(player.getUuid(), playerRef);
        }

        this.vanillaRefs.clear();

        for (SongPlayerRef playerRef : vanillaPlayers) {
            UUID uuid = playerRef.getPlayer().getUuid();
            this.vanillaRefs.put(uuid, playerRef);
        }

        if (vanillaPlayers.isEmpty()) return;

        // there are vanilla players, a server playback is needed
        serverNotePlayer = new ServerBasicNotePlayer(vanillaPlayers, soundProvider, volume);

        final IndividualSongPlayback playback = new IndividualSongPlayback(checkedSong.song(), serverNotePlayer);

        playback.whenDone(() -> {
            this.vanillaRefs.clear();

            checkDestroyed();
        });

        serverPlayback = playback;
        playback.start(startTick);
    }

    private void sendPlayPacket(ServerPlayerEntity player) {
        Song song = checkedSong.song();
        SongHeader header = new SongHeader(song);

        // send the first 5 seconds along with the play packet, so that the client can start playing instantly
        SongSlice slice = SongSlicer.sliceSeconds(song, startTick, 5);
        boolean finished = SongSlicer.isFinished(song, slice);

        var options = new SongPlayOptions(checkedSong.id(), volume, startTick);
        var packet = new PlaySongS2CPacket(options, header, slice, finished, checkedSong.checksum());
        ServerPlayNetworking.send(player, packet);
    }

    private void sendStopPacket(ServerPlayerEntity player) {
        var packet = new StopSongBidiPacket(checkedSong.id());
        ServerPlayNetworking.send(player, packet);
    }

    private void sendSeekPacket(ServerPlayerEntity player, int ticks, boolean absolute) {
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

        onDestroy.invoker().run();
    }

    @Override
    public synchronized Set<ServerPlayerEntity> getListeners() {
        Set<ServerPlayerEntity> listeners = new HashSet<>();

        for (SongPlayerRef playerRef : moddedRefs.values()) {
            listeners.add(playerRef.getPlayer());
        }

        for (SongPlayerRef playerRef : vanillaRefs.values()) {
            listeners.add(playerRef.getPlayer());
        }

        return listeners;
    }

    @Override
    public synchronized boolean isListener(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        return moddedRefs.containsKey(uuid) || vanillaRefs.containsKey(uuid);
    }

    @Override
    public synchronized void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

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

        if (vanillaRefs.isEmpty() && serverPlayback != null) {
            // no server players remaining, stop server playback

            serverPlayback.stop();
            serverPlayback = null;

            serverNotePlayer = null;
        }

        checkDestroyed();
    }

    @Override
    public synchronized void onDestroy(Runnable action) {
        onDestroy.register(action);
    }

    @Override
    public synchronized void onStoppedPlayback(ServerPlayerEntity player) {
        moddedRefs.remove(player.getUuid());

        checkDestroyed();
    }

    private void checkDestroyed() {
        if (!moddedRefs.isEmpty() || !vanillaRefs.isEmpty()) return;

        onDestroy.invoker().run();
    }

    @Override
    public String toString() {
        return "ServerSongHandle{checkedSong=%s, volume=%s, vanillaPlayers=%s, moddedPlayers=%s, started=%s}"
                .formatted(checkedSong, volume, vanillaRefs, moddedRefs, started);
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
}
