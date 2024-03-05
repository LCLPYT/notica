package work.lclpnet.kibu.nbs;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.nbs.cmd.MusicCommand;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class KibuNbsInit implements ModInitializer {

	public static final String MOD_ID = "kibu-nbs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Path songsDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("songs");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				new MusicCommand(songsDirectory, LOGGER).register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTING.register(server ->
				KibuNbsApiImpl.init(server, songsDirectory, LOGGER));

		LOGGER.info("Initialized.");
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