package work.lclpnet.kibu.nbs.impl;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.KibuNbsAPI;
import work.lclpnet.kibu.nbs.api.ExtendedOctaveRange;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.PlayerHolder;
import work.lclpnet.kibu.nbs.api.SongDecoder;
import work.lclpnet.kibu.nbs.controller.Controller;
import work.lclpnet.kibu.nbs.controller.RemoteController;
import work.lclpnet.kibu.nbs.controller.ServerController;
import work.lclpnet.kibu.nbs.data.Song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public class KibuNbsApiImpl implements KibuNbsAPI {

    private static KibuNbsApiImpl instance = null;
    private static Logger logger = null;
    private static Path songsDirectory = null;
    private final MinecraftServer server;
    private final SimpleSongResolver songResolver;
    private final InstrumentSoundProvider soundProvider;
    private final Map<UUID, Controller> remotes = new HashMap<>();

    public static void configure(Path songsDirectory, Logger logger) {
        KibuNbsApiImpl.songsDirectory = Objects.requireNonNull(songsDirectory, "Songs directory is null");
        KibuNbsApiImpl.logger = Objects.requireNonNull(logger, "Logger is null");
    }

    private KibuNbsApiImpl(MinecraftServer server) {
        if (logger == null || songsDirectory == null) {
            throw new IllegalStateException("Not configured yet. KibuNbsApiImpl::configure should be called first");
        }

        this.server = server;
        this.songResolver = new SimpleSongResolver();
        this.soundProvider = new FabricInstrumentSoundProvider(server);
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        remotes.remove(player.getUuid());
    }

    public void execute(Collection<? extends ServerPlayerEntity> players, Consumer<Controller> action) {
        for (ServerPlayerEntity player : players) {
            Controller controller = remotes.computeIfAbsent(player.getUuid(), uuid -> createController(player));

            if (controller instanceof PlayerHolder playerHolder) {
                // update the player reference, in case the player instance changed by respawning
                playerHolder.setPlayer(player);
            }

            action.accept(controller);
        }

    }

    private Controller createController(ServerPlayerEntity player) {
        if (hasModInstalled(player)) {
            return new RemoteController(player.networkHandler);
        }

        ExtendedOctaveRange extendedOctaveRange = () -> true;  // TODO customize per player
        return new ServerController(player, songResolver, soundProvider, extendedOctaveRange, logger);
    }

    private boolean hasModInstalled(ServerPlayerEntity player) {
        // TODO
        return false;
    }

    public static KibuNbsApiImpl getInstance(MinecraftServer server) {
        Objects.requireNonNull(server, "Server must not be null");

        if (instance == null || instance.server != server) {
            instance = new KibuNbsApiImpl(server);
        }

        return instance;
    }

    public CompletableFuture<SongDescriptor> loadSongFile(Path path, Identifier id) {
        return CompletableFuture.supplyAsync(() -> {
            Song song;
            try (var in = Files.newInputStream(path)) {
                song = SongDecoder.parse(in);
            } catch (IOException e) {
                throw new CompletionException(e);
            }

            songResolver.addSong(id, song);

            return new SongDescriptor(id, new byte[0]);  // TODO checksum
        });
    }
}
