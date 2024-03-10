package work.lclpnet.kibu.nbs;

import net.fabricmc.api.ClientModInitializer;
import work.lclpnet.kibu.nbs.event.ClientDisconnectCallback;
import work.lclpnet.kibu.nbs.event.ClientJoinGameCallback;
import work.lclpnet.kibu.nbs.impl.ClientController;
import work.lclpnet.kibu.nbs.impl.ClientInstrumentSoundProvider;
import work.lclpnet.kibu.nbs.impl.ClientSongResolver;
import work.lclpnet.kibu.nbs.networking.KibuNbsClientNetworking;

public class KibuNbsClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientSongResolver songResolver = new ClientSongResolver();
		ClientInstrumentSoundProvider soundProvider = new ClientInstrumentSoundProvider();
		ClientController controller = new ClientController(songResolver, KibuNbsInit.LOGGER, soundProvider);

		new KibuNbsClientNetworking(songResolver, controller, KibuNbsInit.LOGGER).register();

		ClientJoinGameCallback.EVENT.register(networkHandler -> soundProvider.setRegistryManager(networkHandler.getRegistryManager()));
		ClientDisconnectCallback.EVENT.register(() -> soundProvider.setRegistryManager(null));
	}
}