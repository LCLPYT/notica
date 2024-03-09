package work.lclpnet.kibu.nbs;

import net.fabricmc.api.ClientModInitializer;
import work.lclpnet.kibu.nbs.networking.KibuNbsClientNetworking;

public class KibuNbsClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		new KibuNbsClientNetworking().register();
	}
}