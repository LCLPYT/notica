package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.network.SongHeader;
import work.lclpnet.notica.network.SongSlicer;
import work.lclpnet.notica.network.packet.PlaySongS2CPacket;
import work.lclpnet.notica.network.packet.StopSongBidiPacket;

import java.util.*;

public class ServerSongHandle implements SongHandle, PlayerStoppedPlaybackListener {

    private final CheckedSong checkedSong;
    private final float volume;
    private final int startTick;
    private final Map<UUID, SongPlayerRef> vanillaRefs = new HashMap<>(), moddedRefs = new HashMap<>();
    private boolean started = false;
    @Nullable
    private SongPlayback serverPlayback = null;
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

    public void start(Set<SongPlayerRef> vanillaPlayers, Set<SongPlayerRef> moddedPlayers, InstrumentSoundProvider soundProvider) {
        synchronized (this) {
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

            final SongPlayback playback = new SongPlayback(checkedSong.song(), serverNotePlayer);

            playback.whenDone(() -> {
                this.vanillaRefs.clear();

                checkDestroyed();
            });

            serverPlayback = playback;
            playback.start(startTick);
        }
    }

    private void sendPlayPacket(ServerPlayerEntity player) {
        Song song = checkedSong.song();
        SongHeader header = new SongHeader(song);

        // send the first 5 seconds along with the play packet, so that the client can start playing instantly
        SongSlice slice = SongSlicer.sliceSeconds(song, startTick, 5);
        boolean finished = SongSlicer.isFinished(song, slice);

        var packet = new PlaySongS2CPacket(checkedSong.id(), volume, startTick, checkedSong.checksum(), header, finished, slice);
        ServerPlayNetworking.send(player, packet);
    }

    private void sendStopPacket(ServerPlayerEntity player) {
        var packet = new StopSongBidiPacket(checkedSong.id());
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public Identifier getSongId() {
        return checkedSong.id();
    }

    @Override
    public void stop() {
        synchronized (this) {
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
    }

    @Override
    public Set<ServerPlayerEntity> getListeners() {
        Set<ServerPlayerEntity> listeners = new HashSet<>();

        synchronized (this) {
            for (SongPlayerRef playerRef : moddedRefs.values()) {
                listeners.add(playerRef.getPlayer());
            }

            for (SongPlayerRef playerRef : vanillaRefs.values()) {
                listeners.add(playerRef.getPlayer());
            }
        }

        return listeners;
    }

    @Override
    public boolean isListener(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        synchronized (this) {
            return moddedRefs.containsKey(uuid) || vanillaRefs.containsKey(uuid);
        }
    }

    @Override
    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        synchronized (this) {
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
    }

    @Override
    public void onDestroy(Runnable action) {
        synchronized (this) {
            onDestroy.register(action);
        }
    }

    @Override
    public void onStoppedPlayback(ServerPlayerEntity player) {
        synchronized (this) {
            moddedRefs.remove(player.getUuid());

            checkDestroyed();
        }
    }

    private void checkDestroyed() {
        if (!moddedRefs.isEmpty() || !vanillaRefs.isEmpty()) return;

        onDestroy.invoker().run();
    }

    @Override
    public String toString() {
        return "ServerSongHandle{" +
               "checkedSong=" + checkedSong +
               ", volume=" + volume +
               ", vanillaPlayers=" + vanillaRefs +
               ", moddedPlayers=" + moddedRefs +
               ", started=" + started +
               '}';
    }
}
