package work.lclpnet.notica;

import net.fabricmc.api.ClientModInitializer;
import work.lclpnet.notica.event.ClientDisconnectCallback;
import work.lclpnet.notica.event.ClientJoinGameCallback;
import work.lclpnet.notica.impl.ClientInstrumentSoundProvider;
import work.lclpnet.notica.impl.ClientMusicBackend;
import work.lclpnet.notica.impl.ClientSongRepository;
import work.lclpnet.notica.networking.NoticaClientNetworking;
import work.lclpnet.notica.util.PlayerConfigEntry;

public class NoticaClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
        var songRepo = new ClientSongRepository();
        var soundProvider = new ClientInstrumentSoundProvider();

        var playerConfig = new PlayerConfigEntry();
		playerConfig.setExtendedRangeSupported(true);

        var controller = new ClientMusicBackend(songRepo, soundProvider, playerConfig, NoticaInit.LOGGER);

		new NoticaClientNetworking(songRepo, controller, playerConfig, NoticaInit.LOGGER).register();

		ClientJoinGameCallback.EVENT.register(networkHandler -> soundProvider.setRegistryManager(networkHandler.getRegistryManager()));
		ClientDisconnectCallback.EVENT.register(() -> {
			controller.stopAll();
			soundProvider.setRegistryManager(null);
        });
	}
}