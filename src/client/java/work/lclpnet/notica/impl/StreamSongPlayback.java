package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.Source;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.notica.api.IndividualSongPlayback;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.type.NoticaSourceManager;

import static java.util.concurrent.CompletableFuture.runAsync;

public class StreamSongPlayback implements SongPlayback {

    private final SongAudioStream audioStream;
    private final SoundMixer soundMixer;
    private final SoundSampleManager sampleManager;
    private final Channel channel;
    private final Logger logger;

    private volatile Hook<Runnable> onComplete = null;
    private Channel.SourceManager sourceManager = null;
    private boolean stopped = false;
    private Runnable onStopped = null;

    public StreamSongPlayback(SongAudioStream audioStream, SoundMixer soundMixer, SoundSampleManager sampleManager,
                              Channel channel, Logger logger) {
        this.audioStream = audioStream;
        this.soundMixer = soundMixer;
        this.sampleManager = sampleManager;
        this.channel = channel;
        this.logger = logger;
    }

    @Override
    public synchronized void start(int startTick) {
        audioStream.setTick(startTick, true);

        runAsync(sampleManager::loadAll).thenRun(() -> {
            prepareSync();
            playSound();
        }).exceptionally(err -> {
            logger.error("Failed to start playback", err);
            return null;
        });
    }

    private void prepareSync() {
        audioStream.prepare(audioStream.getBufferBytes() * 4);
    }

    @Override
    public synchronized void stop() {
        if (sourceManager == null) return;

        stopped = true;

        sourceManager.run(Source::stop);
        sourceManager = null;
    }

    @Override
    public synchronized void seekTo(int tick, boolean absolute) {
        if (sourceManager == null) return;

        sourceManager.run(source -> {
            if (source.isStopped()) return;

            ((NoticaSourceManager) sourceManager).notica$onStopped(null);

            source.stop();

            sourceManager = null;
            onStopped = null;

            soundMixer.reset();

            audioStream.setTick(tick, absolute);

            Thread.startVirtualThread(() -> {
                prepareSync();
                playSound();
            });
        });
    }

    @Override
    public boolean wasStoppedManually() {
        return stopped;
    }

    @Override
    public void whenDone(Runnable action) {
        getOrCreateHook().register(action);
    }

    private void playSound() {
        channel.createSource(SoundEngine.RunMode.STREAMING).thenAccept(sourceManager -> {
            this.sourceManager = sourceManager;

            onStopped = () -> {
                if (onComplete != null) {
                    onComplete.invoker().run();
                }
            };

            ((NoticaSourceManager) sourceManager).notica$onStopped(onStopped);

            sourceManager.run(source -> {
                source.setRelative(true);
                source.setPosition(Vec3d.ZERO);
                source.setStream(audioStream);
                source.play();
            });
        });
    }

    private Hook<Runnable> getOrCreateHook() {
        if (onComplete != null) return onComplete;

        synchronized (this) {
            if (onComplete != null) return onComplete;

            onComplete = IndividualSongPlayback.runnableHook();
        }

        return onComplete;
    }
}
