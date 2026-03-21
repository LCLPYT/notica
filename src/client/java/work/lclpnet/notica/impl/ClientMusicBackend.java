package work.lclpnet.notica.impl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.config.ConfigManager;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.config.NoticaClientConfig;
import work.lclpnet.notica.config.PlaybackVariantOverride;
import work.lclpnet.notica.config.StereoModeOverride;
import work.lclpnet.notica.impl.mix.*;
import work.lclpnet.notica.mixin.client.SoundBufferLibraryAccessor;
import work.lclpnet.notica.mixin.client.SoundEngineAccessor;
import work.lclpnet.notica.mixin.client.SoundManagerAccessor;
import work.lclpnet.notica.network.SongPlayOptions;
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

    public void playSong(PendingSong song, SongPlayOptions playOptions) {
        Identifier songId = playOptions.songId();
        PlaybackOptions options = playOptions.playbackOptions();

        songRepository.bind(song, songId);

        stopSong(songId);

        NoticaClientConfig config = configManager.config();

        PlaybackVariant variant = Optional.ofNullable(config.getPlaybackVariantOverride())
                .map(PlaybackVariantOverride::variant)
                .orElseGet(options::playbackVariant);

        SongPlayback playback;

        if (variant == PlaybackVariant.STREAMED) {
            playback = createStreamPlayback(song, options);
        } else {
            playback = createIndividualPlayback(song, playOptions);
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

        playback.start(playOptions.startTick());
    }

    private @NotNull IndividualSongPlayback createIndividualPlayback(PendingSong song, SongPlayOptions playOptions) {
        PlaybackOptions options = playOptions.playbackOptions();

        SoundPositionProvider positionProvider = playOptions.speaker()
                .map(SoundPositionProvider::ofSpeaker)
                .orElseGet(SoundPositionProvider::clientPlayerRelative);

        boolean soundHasPosition = playOptions.speaker().isPresent();

        NotePlayer notePlayer = new ClientAggregatingNotePlayer(
                soundProvider,
                options.volume(),
                playerConfig,
                directSoundManager,
                positionProvider,
                soundHasPosition
        );

        return new IndividualSongPlayback(song, notePlayer, options.loopOverride());
    }

    private StreamSongPlayback createStreamPlayback(PendingSong song, PlaybackOptions options) {
        StereoMode stereoMode = Optional.ofNullable(configManager.config().getStereoModeOverride())
                .map(StereoModeOverride::stereoMode)
                .orElseGet(options::stereoMode);

        Minecraft client = Minecraft.getInstance();
        SoundManager soundManager = client.getSoundManager();
        SoundEngine soundSystem = ((SoundManagerAccessor) soundManager).getSoundEngine();
        var soundSystemAccess = (SoundEngineAccessor) soundSystem;

        ChannelAccess channel = soundSystemAccess.getChannelAccess();
        SoundBufferLibrary soundLoader = soundSystemAccess.getSoundBuffers();
        ResourceProvider resourceFactory = ((SoundBufferLibraryAccessor) soundLoader).getResourceManager();

        var sampleProvider = new FabricSoundSampleProvider(song.instruments(), soundProvider, soundManager,
                directSoundManager, resourceFactory, logger);

        var sampleManager = new SoundSampleManager(song.instruments(), sampleProvider, unifiedSoundLoader, CatmullRomNoteSampler::paddedSample);
        var noteSampler = new CatmullRomNoteSampler(sampleManager, unifiedAudioFormat, stereoMode, song.instruments());

        return new StreamSongPlayback(() -> {
            int bufferBytes = SongAudioStream.getByteSize(unifiedAudioFormat, 1.f);
            int workerCount = Runtime.getRuntime().availableProcessors();

            var soundMixer = new SoundMixer(unifiedAudioFormat, noteSampler, bufferBytes, workerCount);
            var songMixer = new ParallelBatchSongMixer(soundMixer, song, workerCount);

            var audioStream = new SongAudioStream(unifiedAudioFormat, soundMixer, songMixer, song,
                    soundMixer::applyCompressor, logger, bufferBytes, options.loopOverride(), false);

            audioStream.setOnUpdate(() -> {
                float categoryVolume = client.options.getFinalSoundSourceVolume(SoundSource.RECORDS);
                float totalVolume = max(0.f, min(1.f, options.volume() * categoryVolume * playerConfig.getVolume()));

                songMixer.setSongVolume(totalVolume);
            });

            return audioStream;
        }, sampleManager, song, channel, logger);
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

    public synchronized void reload() {
        for (SongPlayback playback : playing.values()) {
            if (playback instanceof StreamSongPlayback streamPlayback) {
                streamPlayback.reload();
            }
        }
    }

    public boolean isSongPlaying() {
        return !playing.isEmpty();
    }
}
