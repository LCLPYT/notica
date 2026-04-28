package work.lclpnet.notica.impl;

import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.network.NoticaNetworking;
import work.lclpnet.notica.network.packet.MusicOptionsS2CPacket;
import work.lclpnet.notica.util.ActiveSongManager;
import work.lclpnet.notica.util.PlayerConfigContainer;
import work.lclpnet.notica.util.PlayerConfigEntry;
import work.lclpnet.notica.util.SongPlaybackListener;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public class NoticaImpl implements Notica {

    private static NoticaImpl instance = null;
    private static Logger logger = null;
    private static Path songsDir = null, playerConfigDir = null;
    private final MinecraftServer server;
    private final InstrumentSoundProvider soundProvider;
    @Getter
    private final PlayerConfigContainer playerConfigs;
    private final Map<UUID, SongPlayerRef> playerRefs = new HashMap<>();
    private final Map<Identifier, Song> songsById = new HashMap<>();
    private final ActiveSongManager activeSongManager = new ActiveSongManager();
    private final Map<Identifier, SongHandle> handlesById = new HashMap<>();
    private final Set<PlayerStoppedPlaybackListener> playbackListeners = new HashSet<>();

    public static void configure(Path songsDir, Path playerConfigDir, Logger logger) {
        NoticaImpl.songsDir = requireNonNull(songsDir, "Songs directory is null");
        NoticaImpl.playerConfigDir = requireNonNull(playerConfigDir, "Player Config directory is null");
        NoticaImpl.logger = requireNonNull(logger, "Logger is null");
    }

    private NoticaImpl(MinecraftServer server) {
        if (logger == null || songsDir == null || playerConfigDir == null) {
            throw new IllegalStateException("Not configured yet. NoticaImpl::configure should be called first");
        }

        this.server = server;
        this.soundProvider = new FabricInstrumentSoundProvider(server);
        this.playerConfigs = new PlayerConfigContainer(playerConfigDir, logger);

        new SongPlaybackListener(activeSongManager).init();
    }

    @Override
    public @NonNull SongHandle playSong(CheckedSong song, PlaybackOptions options, int startTick, Collection<? extends ServerPlayer> players) {
        return createSongHandle(song, options, startTick, null, players);
    }

    @Override
    public @NonNull SongHandle playSongWithSpeaker(
            CheckedSong song,
            PlaybackOptions options,
            int startTick,
            Speaker speaker,
            Collection<? extends ServerPlayer> players
    ) {
        return createSongHandle(song, options, startTick, speaker, players);
    }

    private synchronized @NonNull ServerSongHandle createSongHandle(
            CheckedSong song,
            PlaybackOptions options,
            int startTick,
            @Nullable Speaker speaker,
            Collection<? extends ServerPlayer> players
    ) {
        Identifier id = song.id();

        cleanSong(id);

        songsById.put(id, song.song());

        boolean global = players.isEmpty();

        ServerSongHandle handle = new ServerSongHandle(song, options, startTick, speaker, this::createRef, global);

        handle.onDestroy(() -> {
            activeSongManager.removeHandle(handle);

            synchronized (this) {
                playbackListeners.remove(handle);

                cleanSong(id);
            }
        });

        if (global) {
            activeSongManager.addGlobal(handle);

            players = PlayerLookup.all(server);
        } else {
            activeSongManager.add(handle);
        }

        playbackListeners.add(handle);

        handlesById.put(id, handle);

        handle.start(players, soundProvider);

        return handle;
    }

    private void cleanSong(Identifier id) {
        SongHandle handle = handlesById.remove(id);

        if (handle != null) {
            handle.stop();
        }

        if (!handlesById.containsKey(id)) {
            songsById.remove(id);
        }
    }

    @Override
    public @NonNull Set<SongHandle> getPlayingSongs() {
        return Collections.unmodifiableSet(activeSongManager.getAllHandles());
    }

    @Override
    public synchronized @NonNull Set<SongHandle> getPlayingSongs(ServerPlayer player) {
        return getPlayingSongs().stream()
                .filter(handle -> handle.isListener(player))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public synchronized @NonNull Optional<SongHandle> getPlayingSong(Identifier songId) {
        return Optional.ofNullable(handlesById.get(songId));
    }

    @Override
    public synchronized @NonNull Optional<SongHandle> getPlayingSong(ServerPlayer player, Identifier songId) {
        return getPlayingSong(songId).filter(handle -> handle.isListener(player));
    }

    public void onPlayerJoin(ServerPlayer player) {
        synchronized (this) {
            playerConfigs.onPlayerJoin(player);
        }

        syncPlayerConfig(player);
    }

    public synchronized void onPlayerQuit(ServerPlayer player) {
        playerConfigs.onPlayerQuit(player);
        playerRefs.remove(player.getUUID());

        for (SongHandle handle : activeSongManager.getAllHandles()) {
            handle.remove(player);
        }
    }

    public void onPlayerChange(ServerPlayer to) {
        UUID uuid = to.getUUID();
        SongPlayerRef ref = playerRefs.get(uuid);

        if (ref != null) {
            ref.updatePlayer(to);
        }
    }

    public static boolean hasModInstalled(ServerPlayer player) {
        return NoticaNetworking.getInstance().understandsProtocol(player);
    }

    public static NoticaImpl getInstance(MinecraftServer server) {
        requireNonNull(server, "Server must not be null");

        if (instance == null || instance.server != server) {
            instance = new NoticaImpl(server);
        }

        return instance;
    }

    public void syncPlayerConfig(ServerPlayer player) {
        if (!hasModInstalled(player)) return;

        PlayerConfigEntry config = getPlayerConfigs().get(player);
        var packet = new MusicOptionsS2CPacket(config);
        ServerPlayNetworking.send(player, packet);
    }

    private synchronized SongPlayerRef createRef(ServerPlayer player) {
        return playerRefs.computeIfAbsent(player.getUUID(), uuid -> {
            PlayerConfigEntry config = playerConfigs.get(player);
            return new SongPlayerRef(player, config);
        });
    }

    public Optional<Song> getSong(Identifier id) {
        return Optional.ofNullable(songsById.get(id));
    }

    public void notifySongStopped(ServerPlayer player, Identifier songId) {
        var listeners = new HashSet<>(playbackListeners);

        for (PlayerStoppedPlaybackListener listener : listeners) {
            if (!listener.getSongId().equals(songId) || !listener.isListener(player)) continue;

            listener.onStoppedPlayback(player);
        }
    }
}
