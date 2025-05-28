package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.Source;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.notica.api.IndividualSongPlayback;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.type.NoticaSourceManager;

import static java.lang.Math.max;
import static java.util.concurrent.CompletableFuture.runAsync;

public class StreamSongPlayback implements SongPlayback {

    private final SongAudioStream audioStream;
    private final SoundMixer soundMixer;
    private final SoundSampleManager sampleManager;
    private final Song song;
    private final Channel channel;
    private final Logger logger;

    private volatile Hook<Runnable> onComplete = null;
    private Channel.SourceManager sourceManager = null;
    private boolean stopped = false;
    private Runnable onStopped = null;
    private long playbackStartMs = 0;
    private int playbackOffsetTicks = 0;

    public StreamSongPlayback(SongAudioStream audioStream, SoundMixer soundMixer, SoundSampleManager sampleManager,
                              Song song, Channel channel, Logger logger) {
        this.audioStream = audioStream;
        this.soundMixer = soundMixer;
        this.sampleManager = sampleManager;
        this.song = song;
        this.channel = channel;
        this.logger = logger;
    }

    @Override
    public synchronized void start(int startTick) {
        playbackOffsetTicks = 0;

        audioStream.setTick(startTick);

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

        final int currentPlaybackTick = currentPlaybackTick();
        final int startTick = max(0, absolute ? tick : currentPlaybackTick + tick);

        System.out.println(currentPlaybackTick + " -> " + startTick);

        sourceManager.run(source -> {
            if (source.isStopped()) return;

            ((NoticaSourceManager) sourceManager).notica$onStopped(null);

            source.stop();

            sourceManager = null;
            onStopped = null;

            soundMixer.reset();

            audioStream.setTick(startTick);
            playbackOffsetTicks = startTick;

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

                playbackStartMs = milliTime();

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

    private int currentPlaybackTick() {
        long currentMs = milliTime();
        long passedMs = max(0, currentMs - playbackStartMs);

        int passedTicks = song.tempo().durationTicks(playbackOffsetTicks, passedMs / 1000f);

        return (playbackOffsetTicks + passedTicks) % song.durationTicks();
    }

    private static long milliTime() {
        // nanoTime() instead of currentTimeMillis(), because it's monotonic and we only care about relative times
        return System.nanoTime() / 1_000_000;
    }
}
