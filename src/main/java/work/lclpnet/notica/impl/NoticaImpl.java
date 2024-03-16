package work.lclpnet.notica.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.PlayerStoppedPlaybackListener;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.network.NoticaNetworking;
import work.lclpnet.notica.network.packet.MusicOptionsS2CPacket;
import work.lclpnet.notica.util.PlayerConfigContainer;
import work.lclpnet.notica.util.PlayerConfigEntry;

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
    private final PlayerConfigContainer playerConfigs;
    private final Map<UUID, SongPlayerRef> playerRefs = new HashMap<>();
    private final Map<Identifier, Song> songsById = new HashMap<>();
    private final Set<SongHandle> handles = new HashSet<>();
    private final Multimap<Identifier, SongHandle> handlesById = ArrayListMultimap.create();
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
    }

    @Override
    public SongHandle playSong(CheckedSong song, float volume, int startTick, Collection<? extends ServerPlayerEntity> players) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Listeners are empty");
        }

        Identifier id = song.id();
        songsById.put(id, song.song());

        ServerSongHandle handle = new ServerSongHandle(song, volume, startTick);

        Set<SongPlayerRef> moddedPlayers = new HashSet<>();
        Set<SongPlayerRef> vanillaPlayers = new HashSet<>();

        for (ServerPlayerEntity player : players) {
            // check if the playing is already listening to this song
            getPlayingSong(player, id).ifPresent(other -> other.remove(player));

            SongPlayerRef ref = createRef(player);

            if (hasModInstalled(player)) {
                moddedPlayers.add(ref);
            } else {
                vanillaPlayers.add(ref);
            }
        }

        handle.onDestroy(() -> {
            handles.remove(handle);
            playbackListeners.remove(handle);

            handlesById.remove(id, handle);

            cleanSong(id);
        });

        handles.add(handle);
        playbackListeners.add(handle);

        handlesById.put(id, handle);

        handle.start(vanillaPlayers, moddedPlayers, soundProvider);

        return handle;
    }

    private void cleanSong(Identifier id) {
        if (!handlesById.containsKey(id)) {
            songsById.remove(id);
        }
    }

    @Override
    public Set<SongHandle> getPlayingSongs() {
        return Collections.unmodifiableSet(handles);
    }

    @Override
    public Set<SongHandle> getPlayingSongs(ServerPlayerEntity player) {
        return getPlayingSongs().stream()
                .filter(handle -> handle.isListener(player))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<SongHandle> getPlayingSongs(Identifier songId) {
        return getPlayingSongs().stream()
                .filter(handle -> handle.getSongId().equals(songId))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Optional<SongHandle> getPlayingSong(ServerPlayerEntity player, Identifier songId) {
        return getPlayingSongs().stream()
                .filter(handle -> handle.isListener(player) && handle.getSongId().equals(songId))
                .findAny();
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        playerConfigs.onPlayerJoin(player);
        syncPlayerConfig(player);
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        playerConfigs.onPlayerQuit(player);
        playerRefs.remove(player.getUuid());
    }

    public void onPlayerChange(ServerPlayerEntity to) {
        UUID uuid = to.getUuid();
        SongPlayerRef ref = playerRefs.get(uuid);

        if (ref != null) {
            ref.updatePlayer(to);
        }
    }

    public boolean hasModInstalled(ServerPlayerEntity player) {
        return NoticaNetworking.canSendAll(player);
    }

    public PlayerConfigContainer getPlayerConfigs() {
        return playerConfigs;
    }

    public static NoticaImpl getInstance(MinecraftServer server) {
        requireNonNull(server, "Server must not be null");

        if (instance == null || instance.server != server) {
            instance = new NoticaImpl(server);
        }

        return instance;
    }

    public void syncPlayerConfig(ServerPlayerEntity player) {
        if (!hasModInstalled(player)) return;

        PlayerConfigEntry config = getPlayerConfigs().get(player);
        var packet = new MusicOptionsS2CPacket(config);
        ServerPlayNetworking.send(player, packet);
    }

    private SongPlayerRef createRef(ServerPlayerEntity player) {
        return playerRefs.computeIfAbsent(player.getUuid(), uuid -> {
            PlayerConfigEntry config = playerConfigs.get(player);
            return new SongPlayerRef(player, config);
        });
    }

    public Optional<Song> getSong(Identifier id) {
        return Optional.ofNullable(songsById.get(id));
    }

    public void notifySongStopped(ServerPlayerEntity player, Identifier songId) {
        var listeners = new HashSet<>(playbackListeners);

        for (PlayerStoppedPlaybackListener listener : listeners) {
            if (!listener.getSongId().equals(songId) || !listener.isListener(player)) continue;

            listener.onStoppedPlayback(player);
        }
    }
}
