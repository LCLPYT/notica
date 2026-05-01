package work.lclpnet.notica;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.util.ModTranslations;
import work.lclpnet.notica.cmd.MusicCommand;
import work.lclpnet.notica.cmd.PlaylistCommand;
import work.lclpnet.notica.config.ConfigManager;
import work.lclpnet.notica.event.ResourcePackStatusCallback;
import work.lclpnet.notica.impl.NoticaImpl;
import work.lclpnet.notica.network.NoticaNetworking;
import work.lclpnet.notica.util.NoticaServerPackManager;
import work.lclpnet.notica.util.PlaylistManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class NoticaInit implements ModInitializer {

	public static final String MOD_ID = "notica";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private NoticaServerPackManager serverPackManager = null;

    @Override
	public void onInitialize() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

		Path songsDir = createSongsDirectory(configDir);
		Path playerConfigsDir = configDir.resolve("players");
		Path configPath = configDir.resolve("config.json");

		NoticaImpl.configure(songsDir, playerConfigsDir, LOGGER);

		Translations translations = getTranslations();
		ConfigManager configManager = getConfigManager(configPath);

		serverPackManager = new NoticaServerPackManager(configManager, translations, LOGGER);

		PlaylistManager playlistManager = new PlaylistManager();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			new MusicCommand(songsDir, translations, serverPackManager, LOGGER).register(dispatcher);
			new PlaylistCommand(songsDir, translations, playlistManager, LOGGER).register(dispatcher);
		});

		NoticaNetworking networking = new NoticaNetworking(LOGGER);
		networking.register();

		PlayerConnectionHooks.JOIN.register(this::onPlayerJoin);
		PlayerConnectionHooks.QUIT.register(this::onPlayerQuit);
		ServerPlayerEvents.COPY_FROM.register(this::copyFromPlayer);
		ResourcePackStatusCallback.HOOK.register(serverPackManager::onResourcePackStatus);

		LOGGER.info("Initialized.");
	}

	private void onPlayerJoin(ServerPlayer player) {
		NoticaImpl.getInstance(player.level().getServer()).onPlayerJoin(player);
	}

	private void onPlayerQuit(ServerPlayer player) {
		NoticaImpl.getInstance(player.level().getServer()).onPlayerQuit(player);
		serverPackManager.onPlayerQuit(player);
	}

	private void copyFromPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		NoticaImpl.getInstance(newPlayer.level().getServer()).onPlayerChange(newPlayer);
	}

	private static Translations getTranslations() {
		var result = ModTranslations.fromAssets(NoticaInit.MOD_ID, LOGGER);
		Translations translations = result.translations();

		result.whenLoaded().thenRun(() -> LOGGER.info("{} translations loaded.", MOD_ID));

		return translations;
	}

	private static ConfigManager getConfigManager(Path configPath) {
		ConfigManager configManager = new ConfigManager(configPath, LOGGER);

		configManager.init().thenRun(() -> LOGGER.info("{} config loaded.", MOD_ID));

		return configManager;
	}

	private Path createSongsDirectory(Path configDir) {
		Path dir = configDir.resolve("songs");

		CompletableFuture.runAsync(() -> createDirectory(dir));

		return dir;
	}

	private void createDirectory(Path dir) {
		if (Files.exists(dir)) return;

		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LOGGER.error("Failed to create directory {}", dir, e);
		}
	}

	/**
	 * Creates an identifier namespaced with the identifier of the mod.
	 * @param path The path.
	 * @return An identifier of this mod with the given path.
	 */
	public static Identifier identifier(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}