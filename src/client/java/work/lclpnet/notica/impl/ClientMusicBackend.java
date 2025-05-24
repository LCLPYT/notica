package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.item.Items;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.NotePlayer;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.impl.mix.CatmullRomNoteSampler;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SongMixer;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.mixin.client.SoundLoaderAccessor;
import work.lclpnet.notica.mixin.client.SoundManagerAccessor;
import work.lclpnet.notica.mixin.client.SoundSystemAccessor;
import work.lclpnet.notica.network.packet.StopSongBidiPacket;
import work.lclpnet.notica.util.PlayerConfigEntry;

import javax.sound.sampled.AudioFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientMusicBackend {

    private final ClientSongRepository songRepository;
    private final InstrumentSoundProvider soundProvider;
    private final PlayerConfigEntry playerConfig;
    private final Logger logger;
    private final Map<Identifier, SongPlayback> playing = new HashMap<>();
    private final DirectSoundManager directSoundManager = new DirectSoundManager();
    private final AudioFormat unifiedAudioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            48_000, 16, 2, 4, 48_000, false);
    private final UnifiedSoundLoader unifiedSoundLoader;

    public ClientMusicBackend(ClientSongRepository songRepository, InstrumentSoundProvider soundProvider,
                              PlayerConfigEntry playerConfig, Logger logger) {
        this.songRepository = songRepository;
        this.soundProvider = soundProvider;
        this.playerConfig = playerConfig;
        this.logger = logger;
        this.unifiedSoundLoader = new UnifiedSoundLoader(unifiedAudioFormat, logger);
    }

    public void playSong(PendingSong song, Identifier songId, float volume, int startTick) {
        songRepository.bind(song, songId);

        stopSong(songId);

        // TODO remove test code
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player != null && player.getMainHandStack().isOf(Items.STICK)) {
            playMixedSamples(song, startTick, volume);
            return;
        }

        NotePlayer notePlayer = new ClientAggregatingNotePlayer(soundProvider, volume, playerConfig, directSoundManager);
        SongPlayback playback = new SongPlayback(song, notePlayer);

        playback.whenDone(() -> {
            songRepository.unbind(song, songId);

            if (playback.isStopped()) return;

            removePlaying(songId);
            notifySongStopped(songId);
        });

        synchronized (this) {
            playing.put(songId, playback);
        }

        playback.start(startTick);
    }

    private void playMixedSamples(PendingSong song, int startTick, float volume) {
        SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
        SoundSystem soundSystem = ((SoundManagerAccessor) soundManager).getSoundSystem();
        var soundSystemAccess = (SoundSystemAccessor) soundSystem;

        Channel channel = soundSystemAccess.getChannel();
        SoundLoader soundLoader = soundSystemAccess.getSoundLoader();
        ResourceFactory resourceFactory = ((SoundLoaderAccessor) soundLoader).getResourceFactory();

        var sampleProvider = new FabricSoundSampleProvider(song.instruments(), soundProvider, soundManager,
                directSoundManager, resourceFactory, logger);

        var sampleManager = new SoundSampleManager(song.instruments(), sampleProvider, unifiedSoundLoader, CatmullRomNoteSampler::paddedSample);
        var noteSampler = new CatmullRomNoteSampler(sampleManager, unifiedAudioFormat, SoundMixer.StereoMode.SPATIAL, song.instruments());

        int bufferBytes = SongAudioStream.getByteSize(unifiedAudioFormat, 1.f);

        var soundMixer = new SoundMixer(unifiedAudioFormat, noteSampler, bufferBytes);
        var songMixer = new SongMixer(soundMixer, song);
        var audioStream = new SongAudioStream(unifiedAudioFormat, soundMixer, songMixer, song, bufferBytes);

        var playback = new MixedSongPlayback(audioStream, song, soundMixer, songMixer, sampleManager, channel, logger);

        playback.start(startTick, volume);
    }

    public void stopSong(Identifier songId) {
        SongPlayback playback = removePlaying(songId);

        if (playback == null) return;

        playback.stop();
    }

    @Nullable
    private synchronized SongPlayback removePlaying(Identifier songId) {
        return playing.remove(songId);
    }

    private void notifySongStopped(Identifier songId) {
        if (!ClientPlayNetworking.canSend(StopSongBidiPacket.ID)) return;

        var packet = new StopSongBidiPacket(songId);
        ClientPlayNetworking.send(packet);
    }

    public Set<Identifier> getPlayingSongs() {
        return new HashSet<>(playing.keySet());
    }

    public void stopAll() {
        for (Identifier songId : getPlayingSongs()) {
            stopSong(songId);
        }
    }

    public synchronized void seekSongTo(Identifier songId, int ticks, boolean absolute) {
        SongPlayback playback = playing.get(songId);

        if (playback == null) return;

        playback.seekTo(ticks, absolute);
    }
}
