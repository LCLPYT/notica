package work.lclpnet.kibu.nbs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.nbs.cmd.MusicCommand;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class KibuNbsInit implements ModInitializer {

	public static final String MOD_ID = "kibu-nbs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Path songsDirectory = createSongsDirectory();

		KibuNbsApiImpl.configure(songsDirectory, LOGGER);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				new MusicCommand(songsDirectory, LOGGER).register(dispatcher));

		PlayerConnectionHooks.QUIT.register(player -> KibuNbsApiImpl.getInstance(player.getServer()).onPlayerQuit(player));

		LOGGER.info("Initialized.");
	}

	private Path createSongsDirectory() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("songs");

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