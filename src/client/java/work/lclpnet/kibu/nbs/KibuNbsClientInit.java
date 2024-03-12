package work.lclpnet.kibu.nbs;

import net.fabricmc.api.ClientModInitializer;
import work.lclpnet.kibu.nbs.event.ClientDisconnectCallback;
import work.lclpnet.kibu.nbs.event.ClientJoinGameCallback;
import work.lclpnet.kibu.nbs.impl.ClientInstrumentSoundProvider;
import work.lclpnet.kibu.nbs.impl.ClientMusicBackend;
import work.lclpnet.kibu.nbs.impl.ClientSongRepository;
import work.lclpnet.kibu.nbs.networking.KibuNbsClientNetworking;
import work.lclpnet.kibu.nbs.util.PlayerConfigEntry;

public class KibuNbsClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientSongRepository songRepo = new ClientSongRepository();
		ClientInstrumentSoundProvider soundProvider = new ClientInstrumentSoundProvider();

		PlayerConfigEntry playerConfig = new PlayerConfigEntry();
		playerConfig.setExtendedRangeSupported(true);

		ClientMusicBackend controller = new ClientMusicBackend(songRepo, KibuNbsInit.LOGGER, soundProvider, playerConfig);

		new KibuNbsClientNetworking(songRepo, controller, playerConfig, KibuNbsInit.LOGGER).register();

		ClientJoinGameCallback.EVENT.register(networkHandler -> soundProvider.setRegistryManager(networkHandler.getRegistryManager()));
		ClientDisconnectCallback.EVENT.register(() -> {
			controller.stopAll();
			soundProvider.setRegistryManager(null);
        });
	}
}