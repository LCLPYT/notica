package work.lclpnet.notica;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.notica.cmd.MusicCommand;
import work.lclpnet.notica.config.ConfigManager;
import work.lclpnet.notica.event.ResourcePackStatusCallback;
import work.lclpnet.notica.impl.NoticaImpl;
import work.lclpnet.notica.network.NoticaNetworking;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.notica.util.NoticaServerPackManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class NoticaInit implements ModInitializer {

	public static final String MOD_ID = "notica";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private NoticaNetworking networking = null;
	private NoticaServerPackManager serverPackManager = null;

	@Override
	public void onInitialize() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

		Path songsDir = createSongsDirectory(configDir);
		Path playerConfigsDir = configDir.resolve("players");
		Path configPath = configDir.resolve("config.json");

		NoticaImpl.configure(songsDir, playerConfigsDir, LOGGER);

		TranslationService translations = getTranslationService();
		ConfigManager configManager = getConfigManager(configPath);

		serverPackManager = new NoticaServerPackManager(configManager, translations, LOGGER);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				new MusicCommand(songsDir, translations,serverPackManager, LOGGER).register(dispatcher));

		networking = new NoticaNetworking(LOGGER);
		networking.register();

		PlayerConnectionHooks.JOIN.register(this::onPlayerJoin);
		PlayerConnectionHooks.QUIT.register(this::onPlayerQuit);
		ServerPlayerEvents.COPY_FROM.register(this::copyFromPlayer);
		ResourcePackStatusCallback.HOOK.register(serverPackManager::onResourcePackStatus);

		LOGGER.info("Initialized.");
	}

	private void onPlayerJoin(ServerPlayerEntity player) {
		NoticaImpl.getInstance(player.getServer()).onPlayerJoin(player);
	}

	private void onPlayerQuit(ServerPlayerEntity player) {
		NoticaImpl.getInstance(player.getServer()).onPlayerQuit(player);
		networking.onQuit(player);
		serverPackManager.onPlayerQuit(player);
	}

	private void copyFromPlayer(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
		NoticaImpl.getInstance(newPlayer.getServer()).onPlayerChange(newPlayer);
	}

	private static TranslationService getTranslationService() {
		var result = ModTranslations.load(LOGGER);
		TranslationService translations = result.translations();

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
		return new Identifier(MOD_ID, path);
	}
}