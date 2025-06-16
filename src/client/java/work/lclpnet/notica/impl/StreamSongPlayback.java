package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.Source;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.notica.api.IndividualSongPlayback;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SoundSampleManager;
import work.lclpnet.notica.type.NoticaSource;
import work.lclpnet.notica.type.NoticaSourceManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static java.lang.Math.max;

public class StreamSongPlayback implements SongPlayback {

    private final Supplier<SongAudioStream> streamSupplier;
    private final SoundSampleManager sampleManager;
    private final Song song;
    private final Channel channel;
    private final Executor mutexExecutor = Executors.newSingleThreadExecutor();

    private volatile Hook<Runnable> onComplete = null;
    private Channel.SourceManager sourceManager = null;
    private boolean stopped = false;
    private Runnable onStopped = null;
    private long playbackStartMs = 0;
    private int playbackOffsetTicks = 0;

    public StreamSongPlayback(Supplier<SongAudioStream> streamSupplier, SoundSampleManager sampleManager,
                              Song song, Channel channel) {
        this.streamSupplier = streamSupplier;
        this.sampleManager = sampleManager;
        this.song = song;
        this.channel = channel;
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

                playbackStartMs = milliTime();

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
        long currentMs = milliTime();
        long passedMs = max(0, currentMs - playbackStartMs);

        int passedTicks = song.tempo().durationTicks(playbackOffsetTicks, passedMs / 1000f);

        return (playbackOffsetTicks + passedTicks) % song.durationTicks();
    }

    private static long milliTime() {
        // nanoTime() instead of currentTimeMillis(), because it's monotonic and we only care about relative times
        return System.nanoTime() / 1_000_000;
    }

    private void mutexNewPlayback(int startTick) {
        playbackOffsetTicks = 0;

        SongAudioStream stream = streamSupplier.get();

        stream.setTick(startTick).join();

        sampleManager.loadAll();

        prepareFirstBuffer(stream).join();

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

                source.stop();

                sourceManager = null;
                onStopped = null;

                future.complete(null);
            });
        }

        future.join();

        mutexNewPlayback(startTick);
    }
}
