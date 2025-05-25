package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.Source;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SongMixer;
import work.lclpnet.notica.impl.mix.SoundMixer;

import static java.util.concurrent.CompletableFuture.runAsync;

public class StreamSongPlayback implements SongPlayback {

    private final SongAudioStream audioStream;
    private final SoundMixer soundMixer;
    private final SongMixer songMixer;
    private final SoundSampleManager sampleManager;
    private final Channel channel;
    private final Logger logger;
    private Channel.SourceManager sourceManager = null;

    public StreamSongPlayback(SongAudioStream audioStream, SoundMixer soundMixer, SongMixer songMixer,
                              SoundSampleManager sampleManager, Channel channel, Logger logger) {
        this.audioStream = audioStream;
        this.soundMixer = soundMixer;
        this.songMixer = songMixer;
        this.sampleManager = sampleManager;
        this.channel = channel;
        this.logger = logger;
    }

    @Override
    public void start(int startTick) {
        audioStream.setTick(startTick);

        runAsync(sampleManager::loadAll).thenRun(() -> {
            Thread prepareThread = audioStream.prepareAsync(audioStream.getBufferBytes() * 4);

            try {
                prepareThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // TODO support volume
            playSound();
        }).exceptionally(err -> {
            logger.error("Failed to start playback", err);
            return null;
        });
    }

    @Override
    public void stop() {
        if (sourceManager == null) return;

        sourceManager.run(Source::stop);
    }

    @Override
    public void seekTo(int tick, boolean absolute) {
        // TODO support absolute
        audioStream.setTick(tick);
        soundMixer.reset();
    }

    @Override
    public boolean isStopped() {
        return false;  // TODO implement
    }

    @Override
    public void whenDone(Runnable action) {
        // TODO implement
    }

    private void playSound() {
        sourceManager = channel.createSource(SoundEngine.RunMode.STREAMING).join();

        sourceManager.run(source -> {
            source.setRelative(true);
            source.setPosition(Vec3d.ZERO);
            source.setStream(audioStream);
            source.play();
        });
    }
}
