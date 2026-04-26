package work.lclpnet.notica.impl;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.notica.NoticaClientInit;
import work.lclpnet.notica.api.IndividualSongPlayback;
import work.lclpnet.notica.api.SongPlayback;
import work.lclpnet.notica.api.Speaker;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SharedSongBuffers;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SongStream;
import work.lclpnet.notica.impl.mix.SoundSampleManager;
import work.lclpnet.notica.mixin.client.ChannelAccessor;
import work.lclpnet.notica.type.NoticaChannel;
import work.lclpnet.notica.type.NoticaChannelHandle;

import javax.sound.sampled.AudioFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.lang.Math.max;

public class StreamSongPlayback implements SongPlayback {

    private static final int TIMEOUT_MS = 10_000;

    private final Supplier<SongStream> streamSupplier;
    private final SoundSampleManager sampleManager;
    private final Song song;
    private final ChannelAccess channelAccess;
    private final @Nullable Speaker speaker;
    private final AudioFormat audioFormat;
    private final int soundCount;
    private final Logger logger;
    private final @Nullable SoundPositionProvider soundPositionProvider;
    private final Executor mutexExecutor = Executors.newSingleThreadExecutor();

    private volatile Hook<Runnable> onComplete = null;
    private ChannelAccess.ChannelHandle[] channelHandles = null;
    private PlaybackTimeTracker timeTracker = null;
    private boolean stopped = false;
    private Runnable onStopped = null;
    private int playbackOffsetTicks = 0;

    public StreamSongPlayback(Supplier<SongStream> streamSupplier, SoundSampleManager sampleManager,
                              Song song, ChannelAccess channelAccess, @Nullable Speaker speaker,
                              AudioFormat audioFormat, int soundCount, Logger logger) {
        this.streamSupplier = streamSupplier;
        this.sampleManager = sampleManager;
        this.song = song;
        this.channelAccess = channelAccess;
        this.speaker = speaker;
        this.audioFormat = audioFormat;
        this.soundCount = soundCount;
        this.logger = logger;

        this.soundPositionProvider = speaker != null ? SoundPositionProvider.ofSpeaker(speaker) : null;

        if (soundCount <= 0) throw new IllegalArgumentException("Need at least one sound");
    }

    @Override
    public void start(int startTick) {
        mutexExecutor.execute(() -> mutexNewPlayback(startTick));
    }

    private CompletableFuture<Void> prepareFirstBuffer(SongStream stream) {
        return stream.startProducer(4);
    }

    @Override
    public synchronized void stop() {
        if (channelHandles == null) return;

        stopped = true;

        for (ChannelAccess.ChannelHandle channelHandle : channelHandles) {
            channelHandle.execute(Channel::stop);
        }

        channelHandles = null;
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

    private CompletableFuture<Void> playSound(AudioStream stream, float bufferSeconds, int channelIndex, float panning) {
        var future = new CompletableFuture<Void>();

        channelAccess.createHandle(Library.Pool.STREAMING).thenAccept(channelHandle -> {
            channelHandles[channelIndex] = channelHandle;

            if (channelHandle == null) return;

            timeTracker = new PlaybackTimeTracker(bufferSeconds);

            channelHandle.execute(c -> ((NoticaChannel) c).notica$onTick(channel -> {
                timeTracker.tick(channel);

                updatePosition(channel, panning);
                updateVelocity(channel);
            }));

            onStopped = () -> {
                if (onComplete != null) {
                    onComplete.invoker().run();
                }
            };

            ((NoticaChannelHandle) channelHandle).notica$onStopped(onStopped);

            channelHandle.execute(channel -> {
                ((NoticaChannel) channel).notica$setNoticaSource();

                if (soundPositionProvider != null) {
                    channel.setRelative(false);
                    channel.linearAttenuation(16);

                    updatePosition(channel, panning);
                    updateVelocity(channel);
                } else {
                    channel.setRelative(true);
                    channel.setSelfPosition(Vec3.ZERO);
                }

                channel.attachBufferStream(stream);

                channel.play();

                future.complete(null);
            });
        }).exceptionally(err -> {
            future.completeExceptionally(err);
            return null;
        });

        return future;
    }

    private void updateVelocity(Channel channel) {
        if (speaker == null || !speaker.dopplerEffect()) return;

        ClientLevel level = Minecraft.getInstance().level;

        if (level == null) return;

        Entity entity = speaker.resolveEntity(level).orElse(null);

        if (entity == null) return;

        double intensity = NoticaClientInit.configManager()
                .map(m -> m.config().getDopplerIntensity())
                .orElse(1.0);

        int source = ((ChannelAccessor) channel).getSource();

        Vec3 velocity = entity.getDeltaMovement();

        // getDeltaMovement() is in blocks/tick; OpenAL expects units/second (20 ticks/s)
        AL10.alSource3f(source, AL10.AL_VELOCITY,
                (float) (velocity.x * 20 * intensity),
                (float) (velocity.y * 20 * intensity),
                (float) (velocity.z * 20 * intensity));
    }

    private void updatePosition(Channel channel, float panning) {
        if (soundPositionProvider == null) return;

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) return;

        Vec3 pos = soundPositionProvider.getPosition(player, panning);

        channel.setSelfPosition(pos);
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

        SongStream stream = streamSupplier.get();

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

        float bufferSeconds = stream.getBufferSeconds();

        if (soundCount == 1) {
            // non-positional stereo audio playback
            var audioStream = new SongAudioStream(stream::nextBuffers, audioFormat, 0, stream::close);

            channelHandles = new ChannelAccess.ChannelHandle[1];
            playSound(audioStream, bufferSeconds, 0, 0f).join();
            return;
        }

        channelHandles = new ChannelAccess.ChannelHandle[soundCount];

        var requiredCloseCalls = new AtomicInteger(soundCount);
        var shared = new SharedSongBuffers(stream, soundCount);

        for (int i = 0; i < soundCount; i++) {
            var audioStream = new SongAudioStream(shared.consumerSupplier(i), audioFormat, i, () -> {
                if (requiredCloseCalls.decrementAndGet() == 0) {
                    stream.close();
                }
            });

            float panning = switch (i) {
                case 0 -> -1;
                case 1 -> +1;
                default -> 0;
            };

            playSound(audioStream, bufferSeconds, i, panning).join();
        }
    }

    private void mutexSeekTo(int tick, boolean absolute) {
        var future = new CompletableFuture<>();
        int startTick;

        synchronized (this) {
            if (channelHandles == null) return;

            final int currentPlaybackTick = currentPlaybackTick();
            startTick = max(0, absolute ? tick : currentPlaybackTick + tick);

            for (ChannelAccess.ChannelHandle channelHandle : channelHandles) {
                channelHandle.execute(channel -> {
                    if (channel.stopped()) return;

                    ((NoticaChannelHandle) channelHandle).notica$onStopped(null);
                    ((NoticaChannel) channel).notica$setStopped();
                    ((NoticaChannel) channel).notica$onTick(null);

                    channel.stop();

                    onStopped = null;
                    timeTracker = null;

                    future.complete(null);
                });
            }

            channelHandles = null;
        }

        future.join();

        mutexNewPlayback(startTick);
    }

    public void reload() {
        seekTo(0, false);
    }
}
