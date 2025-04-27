package work.lclpnet.notica;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.SoundSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.event.ClientDisconnectCallback;
import work.lclpnet.notica.event.ClientJoinGameCallback;
import work.lclpnet.notica.impl.ClientInstrumentSoundProvider;
import work.lclpnet.notica.impl.ClientMusicBackend;
import work.lclpnet.notica.impl.ClientSongRepository;
import work.lclpnet.notica.impl.SoundMixer;
import work.lclpnet.notica.mixin.client.SoundManagerAccessor;
import work.lclpnet.notica.mixin.client.SoundSystemAccessor;
import work.lclpnet.notica.networking.NoticaClientNetworking;
import work.lclpnet.notica.util.PlayerConfigEntry;

public class NoticaClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
        var songRepo = new ClientSongRepository();
        var soundProvider = new ClientInstrumentSoundProvider();

        var playerConfig = new PlayerConfigEntry();
		playerConfig.setExtendedRangeSupported(true);

        var controller = new ClientMusicBackend(songRepo, soundProvider, playerConfig);

		new NoticaClientNetworking(songRepo, controller, playerConfig, NoticaInit.LOGGER).register();

		ClientJoinGameCallback.EVENT.register(networkHandler -> soundProvider.setRegistryManager(networkHandler.getRegistryManager()));
		ClientDisconnectCallback.EVENT.register(() -> {
			controller.stopAll();
			soundProvider.setRegistryManager(null);
        });

		ClientSendMessageEvents.CHAT.register(message -> getSoundMixer().playMerged());
	}

	private @Nullable SoundMixer soundMixer = null;

	private synchronized @NotNull SoundMixer getSoundMixer() {
		if (soundMixer != null) {
			return soundMixer;
		}

		SoundSystem soundSystem = ((SoundManagerAccessor) MinecraftClient.getInstance().getSoundManager()).getSoundSystem();
		var soundSystemAccess = (SoundSystemAccessor) soundSystem;

		SoundLoader soundLoader = soundSystemAccess.getSoundLoader();
		Channel channel = soundSystemAccess.getChannel();

		var soundMixer = new SoundMixer(soundLoader, channel, NoticaInit.LOGGER);

		this.soundMixer = soundMixer;

		return soundMixer;
	}
}