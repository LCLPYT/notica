package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.config.ConfigManager;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.config.NoticaClientConfig;
import work.lclpnet.notica.config.PlaybackVariantOverride;
import work.lclpnet.notica.config.StereoModeOverride;
import work.lclpnet.notica.impl.mix.BatchSongMixer;
import work.lclpnet.notica.impl.mix.CatmullRomNoteSampler;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.mixin.client.SoundLoaderAccessor;
import work.lclpnet.notica.mixin.client.SoundManagerAccessor;
import work.lclpnet.notica.mixin.client.SoundSystemAccessor;
import work.lclpnet.notica.network.packet.StopSongBidiPacket;
import work.lclpnet.notica.util.PlayerConfigEntry;

import javax.sound.sampled.AudioFormat;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class ClientMusicBackend {

    private final ClientSongRepository songRepository;
    private final InstrumentSoundProvider soundProvider;
    private final PlayerConfigEntry playerConfig;
    private final ConfigManager<NoticaClientConfig> configManager;
    private final Logger logger;
    private final Map<Identifier, SongPlayback> playing = new HashMap<>();
    private final DirectSoundManager directSoundManager = new DirectSoundManager();
    private final AudioFormat unifiedAudioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            48_000, 16, 2, 4, 48_000, false);
    private final UnifiedSoundLoader unifiedSoundLoader;

    public ClientMusicBackend(ClientSongRepository songRepository, InstrumentSoundProvider soundProvider,
                              PlayerConfigEntry playerConfig, ConfigManager<NoticaClientConfig> configManager,
                              Logger logger) {
        this.songRepository = songRepository;
        this.soundProvider = soundProvider;
        this.playerConfig = playerConfig;
        this.configManager = configManager;
        this.logger = logger;
        this.unifiedSoundLoader = new UnifiedSoundLoader(unifiedAudioFormat, logger);
    }

    public void playSong(PendingSong song, Identifier songId, PlaybackOptions options, int startTick) {
        songRepository.bind(song, songId);

        stopSong(songId);

        NoticaClientConfig config = configManager.config();

        PlaybackVariant variant = Optional.ofNullable(config.getPlaybackVariantOverride())
                .map(PlaybackVariantOverride::variant)
                .orElseGet(options::variant);

        SongPlayback playback;

        if (variant == PlaybackVariant.STREAMED) {
            StereoMode stereoMode = Optional.ofNullable(config.getStereoModeOverride())
                    .map(StereoModeOverride::stereoMode)
                    .orElseGet(options::stereoMode);

            playback = createStreamPlayback(song, options.volume(), stereoMode);
        } else {
            playback = createIndividualPlayback(song, options.volume());
        }

        playback.whenDone(() -> {
            songRepository.unbind(song, songId);

            if (playback.wasStoppedManually()) return;

            removePlaying(songId);
            notifySongStopped(songId);
        });

        synchronized (this) {
            playing.put(songId, playback);
        }

        playback.start(startTick);
    }

    private @NotNull IndividualSongPlayback createIndividualPlayback(PendingSong song, float volume) {
        NotePlayer notePlayer = new ClientAggregatingNotePlayer(soundProvider, volume, playerConfig, directSoundManager);

        return new IndividualSongPlayback(song, notePlayer);
    }

    private StreamSongPlayback createStreamPlayback(PendingSong song, float volume, StereoMode stereoMode) {
        MinecraftClient client = MinecraftClient.getInstance();
        SoundManager soundManager = client.getSoundManager();
        SoundSystem soundSystem = ((SoundManagerAccessor) soundManager).getSoundSystem();
        var soundSystemAccess = (SoundSystemAccessor) soundSystem;

        Channel channel = soundSystemAccess.getChannel();
        SoundLoader soundLoader = soundSystemAccess.getSoundLoader();
        ResourceFactory resourceFactory = ((SoundLoaderAccessor) soundLoader).getResourceFactory();

        var sampleProvider = new FabricSoundSampleProvider(song.instruments(), soundProvider, soundManager,
                directSoundManager, resourceFactory, logger);

        var sampleManager = new SoundSampleManager(song.instruments(), sampleProvider, unifiedSoundLoader, CatmullRomNoteSampler::paddedSample);
        var noteSampler = new CatmullRomNoteSampler(sampleManager, unifiedAudioFormat, stereoMode, song.instruments());

        int bufferBytes = SongAudioStream.getByteSize(unifiedAudioFormat, 1.f);

        var soundMixer = new SoundMixer(unifiedAudioFormat, noteSampler, bufferBytes);
        var songMixer = new BatchSongMixer(soundMixer, song);
        var audioStream = new SongAudioStream(unifiedAudioFormat, soundMixer, songMixer, song, bufferBytes);

        audioStream.setOnUpdate(() -> {
            float categoryVolume = client.options.getSoundVolume(SoundCategory.RECORDS);
            float totalVolume = max(0.f, min(1.f, volume * categoryVolume * playerConfig.getVolume()));

            songMixer.setSongVolume(totalVolume);
        });

        return new StreamSongPlayback(audioStream, soundMixer, sampleManager, song, channel, logger);
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
