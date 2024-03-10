package work.lclpnet.kibu.nbs;

import net.fabricmc.api.ClientModInitializer;
import work.lclpnet.kibu.nbs.event.ClientDisconnectCallback;
import work.lclpnet.kibu.nbs.event.ClientJoinGameCallback;
import work.lclpnet.kibu.nbs.impl.ClientController;
import work.lclpnet.kibu.nbs.impl.ClientInstrumentSoundProvider;
import work.lclpnet.kibu.nbs.impl.ClientSongResolver;
import work.lclpnet.kibu.nbs.networking.KibuNbsClientNetworking;
import work.lclpnet.kibu.nbs.util.PlayerConfigEntry;

public class KibuNbsClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientSongResolver songResolver = new ClientSongResolver();
		ClientInstrumentSoundProvider soundProvider = new ClientInstrumentSoundProvider();

		PlayerConfigEntry playerConfig = new PlayerConfigEntry();
		playerConfig.setExtendedRangeSupported(true);

		ClientController controller = new ClientController(songResolver, KibuNbsInit.LOGGER, soundProvider, playerConfig);

		new KibuNbsClientNetworking(songResolver, controller, playerConfig, KibuNbsInit.LOGGER).register();

		ClientJoinGameCallback.EVENT.register(networkHandler -> soundProvider.setRegistryManager(networkHandler.getRegistryManager()));
		ClientDisconnectCallback.EVENT.register(() -> {
			controller.stopAll();
			soundProvider.setRegistryManager(null);
        });
	}
}