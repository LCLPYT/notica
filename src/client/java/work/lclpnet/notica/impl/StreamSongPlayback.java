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
import work.lclpnet.notica.impl.mix.SoundSampleManager;
import work.lclpnet.notica.type.NoticaSource;
import work.lclpnet.notica.type.NoticaSourceManager;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static java.lang.Math.max;

public class StreamSongPlayback implements SongPlayback {

    private static final int TIMEOUT_MS = 10_000;

    private final Supplier<SongAudioStream> streamSupplier;
    private final SoundSampleManager sampleManager;
    private final Song song;
    private final Channel channel;
    private final Logger logger;
    private final Executor mutexExecutor = Executors.newSingleThreadExecutor();

    private volatile Hook<Runnable> onComplete = null;
    private Channel.SourceManager sourceManager = null;
    private PlaybackTimeTracker timeTracker = null;
    private boolean stopped = false;
    private Runnable onStopped = null;
    private int playbackOffsetTicks = 0;

    public StreamSongPlayback(Supplier<SongAudioStream> streamSupplier, SoundSampleManager sampleManager,
                              Song song, Channel channel, Logger logger) {
        this.streamSupplier = streamSupplier;
        this.sampleManager = sampleManager;
        this.song = song;
        this.channel = channel;
        this.logger = logger;
    }

    @Override
    public void start(int startTick) {
        mutexExecutor.execute(() -> mutexNewPlayback(startTick));
    }

    private CompletableFuture<Void> prepareFirstBuffer(SongAudioStream stream) {
        return stream.startProducer(4);
    }

    @Override
    public synchronized void stop() {
        if (sourceManager == null) return;

        stopped = true;

        sourceManager.run(Source::stop);
        sourceManager = null;
    }

    @Override
    public void seekTo(int tick, boolean absolute) {
        mutexExecutor.execute(() -> mutexSeekTo(tick, absolute));
    }

    @Override
    public boolean wasStoppedManually() {
        return stopped;
    }

    @Override
    public void whenDone(Runnable action) {
        getOrCreateHook().register(action);
    }

    private CompletableFuture<Void> playSound(SongAudioStream stream) {
        var future = new CompletableFuture<Void>();

        channel.createSource(SoundEngine.RunMode.STREAMING).thenAccept(sourceManager -> {
            this.sourceManager = sourceManager;

            float bufferSeconds = stream.getBufferSeconds();

            timeTracker = new PlaybackTimeTracker(sourceManager, bufferSeconds);
            timeTracker.init();

            onStopped = () -> {
                if (onComplete != null) {
                    onComplete.invoker().run();
                }
            };

            ((NoticaSourceManager) sourceManager).notica$onStopped(onStopped);

            sourceManager.run(source -> {
                ((NoticaSource) source).notica$setNoticaSource();

                source.setRelative(true);
                source.setPosition(Vec3d.ZERO);
                source.setStream(stream);

                source.play();

                future.complete(null);
            });
        }).exceptionally(err -> {
            future.completeExceptionally(err);
            return null;
        });

        return future;
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
        if (timeTracker == null) return 0;

        float playbackSeconds = timeTracker.getPlaybackSeconds();

        int passedTicks = song.tempo().durationTicks(playbackOffsetTicks, playbackSeconds);

        return (playbackOffsetTicks + passedTicks) % song.durationTicks();
    }

    private void mutexNewPlayback(int startTick) {
        playbackOffsetTicks = startTick;

        SongAudioStream stream = streamSupplier.get();

        stream.setTick(startTick).join();

        sampleManager.loadAll();

        var firstBufferFuture = prepareFirstBuffer(stream);

        try {
            firstBufferFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Waiting for first buffer to be prepared", e);
        } catch (TimeoutException e) {
            logger.error("Preparing the first buffer took too long, aborting...", e);
            return;
        }

        playSound(stream).join();
    }

    private void mutexSeekTo(int tick, boolean absolute) {
        var future = new CompletableFuture<>();
        int startTick;

        synchronized (this) {
            if (sourceManager == null) return;

            final int currentPlaybackTick = currentPlaybackTick();
            startTick = max(0, absolute ? tick : currentPlaybackTick + tick);

            sourceManager.run(source -> {
                if (source.isStopped()) return;

                ((NoticaSourceManager) sourceManager).notica$onStopped(null);
                ((NoticaSource) source).notica$setStopped();
                ((NoticaSource) source).notica$onTick(null);

                source.stop();

                sourceManager = null;
                onStopped = null;
                timeTracker = null;

                future.complete(null);
            });
        }

        future.join();

        mutexNewPlayback(startTick);
    }

    public void reload() {
        seekTo(0, false);
    }
}
