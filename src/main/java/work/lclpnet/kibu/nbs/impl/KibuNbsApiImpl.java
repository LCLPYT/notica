package work.lclpnet.kibu.nbs.impl;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.nbs.KibuNbsAPI;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.controller.Controller;
import work.lclpnet.kibu.nbs.controller.RemoteController;
import work.lclpnet.kibu.nbs.controller.ServerController;
import work.lclpnet.kibu.nbs.api.PlayerHolder;
import work.lclpnet.kibu.nbs.api.SongResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class KibuNbsApiImpl implements KibuNbsAPI {

    private static KibuNbsApiImpl instance = null;
    private final Path songsDirectory;
    private final SongResolver songResolver;
    private final Logger logger;
    private final InstrumentSoundProvider soundProvider;
    private final Map<UUID, Controller> remotes = new HashMap<>();

    private KibuNbsApiImpl(MinecraftServer server, Path songsDirectory, Logger logger) {
        this.songsDirectory = songsDirectory;
        this.logger = logger;
        this.songResolver = new CachedSongResolver(new DirectorySongResolver(songsDirectory, ForkJoinPool.commonPool()));
        this.soundProvider = new FabricInstrumentSoundProvider(server);
    }

    private void init() {
        new Thread(this::createDirectories, "Directories Setup").start();
    }

    private void createDirectories() {
        if (Files.exists(songsDirectory)) return;

        try {
            Files.createDirectories(songsDirectory);
        } catch (IOException e) {
            logger.error("Failed to create songs directory at {}", songsDirectory, e);
        }
    }

    private void execute(Collection<? extends ServerPlayerEntity> players, Consumer<Controller> action) {
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

        return new ServerController(player, songResolver, soundProvider, logger);
    }

    private void onPlayerQuit(ServerPlayerEntity player) {
        remotes.remove(player.getUuid());
    }

    private boolean hasModInstalled(ServerPlayerEntity player) {
        // TODO
        return false;
    }

    public static void init(MinecraftServer server, Path songsDirectory, Logger logger) {
        if (instance != null) throw new IllegalStateException("Already initialized");
        instance = new KibuNbsApiImpl(server, songsDirectory, logger);

        instance.init();

        PlayerConnectionHooks.QUIT.register(instance::onPlayerQuit);
    }

    public static KibuNbsApiImpl getInstance() {
        if (instance == null) throw new IllegalStateException("Not initialized yet");
        return instance;
    }
}
