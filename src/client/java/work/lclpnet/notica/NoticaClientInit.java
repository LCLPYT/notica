package work.lclpnet.notica;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import work.lclpnet.kibu.config.ConfigManager;
import work.lclpnet.notica.config.NoticaClientConfig;
import work.lclpnet.notica.event.ClientDisconnectCallback;
import work.lclpnet.notica.event.ClientJoinGameCallback;
import work.lclpnet.notica.impl.ClientInstrumentSoundProvider;
import work.lclpnet.notica.impl.ClientMusicBackend;
import work.lclpnet.notica.impl.ClientSongRepository;
import work.lclpnet.notica.networking.NoticaClientNetworking;
import work.lclpnet.notica.util.PlayerConfigEntry;

import java.nio.file.Path;
import java.util.Optional;

public class NoticaClientInit implements ClientModInitializer {

	private static volatile ConfigManager<NoticaClientConfig> _configManager = null;

	@Override
	public void onInitializeClient() {
		var configManager = loadConfig();
		_configManager = configManager;

        var songRepo = new ClientSongRepository();
        var soundProvider = new ClientInstrumentSoundProvider();

        var playerConfig = new PlayerConfigEntry();
		playerConfig.setExtendedRangeSupported(true);

        var musicBackend = new ClientMusicBackend(songRepo, soundProvider, playerConfig, configManager, NoticaInit.LOGGER);

		new NoticaClientNetworking(songRepo, musicBackend, playerConfig, NoticaInit.LOGGER).register();

		ClientJoinGameCallback.EVENT.register(networkHandler -> soundProvider.setRegistryManager(networkHandler.getRegistryManager()));
		ClientDisconnectCallback.EVENT.register(() -> {
			musicBackend.stopAll();
			soundProvider.setRegistryManager(null);
        });
	}

	private ConfigManager<NoticaClientConfig> loadConfig() {
		Path configPath = FabricLoader.getInstance().getConfigDir()
				.resolve(NoticaInit.MOD_ID)
				.resolve("client.toml");

		var configManager = new ConfigManager<>(configPath, new NoticaClientConfig());

		configManager.load();

		return configManager;
	}

	public static Optional<ConfigManager<NoticaClientConfig>> configManager() {
		return Optional.ofNullable(_configManager);
	}
}