package work.lclpnet.notica.impl.mix;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.sound.AudioStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import work.lclpnet.notica.api.data.LoopConfig;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.ds.BlockingSendReceive;
import work.lclpnet.notica.impl.ds.SemiBlockingSendReceive;
import work.lclpnet.notica.impl.ds.SendReceive;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class SongAudioStream implements AudioStream {

    private static final int
            PREPARE_COUNT = 5,
            TIMEOUT_MS = 10_000;

    private final AudioFormat format;
    private final Song song;
    private final SoundMixer soundMixer;
    private final SongMixer songMixer;
    private final BufferProcessor bufferProcessor;
    private final Logger logger;
    private final ByteBuffer[] preparedBuffers;
    @Getter
    private final int bufferBytes;
    private final SendReceive<ByteBuffer> queue;
    private final boolean loopEnabled;

    private @Nullable Thread producer = null, watchdog = null;
    @Setter
    private Runnable onUpdate = () -> {};
    private boolean first = true;
    private boolean ended = false;
    private int tick = 0;
    private int prepareIdx = 0;
    private int frameOffset = 0;
    private int loopCount;

    public SongAudioStream(AudioFormat format, SoundMixer soundMixer, SongMixer songMixer, Song song,
                           BufferProcessor bufferProcessor, Logger logger, int bufferBytes, boolean loopEnabled,
                           boolean shouldBlock) {
        this.format = format;
        this.soundMixer = soundMixer;
        this.songMixer = songMixer;
        this.song = song;
        this.bufferProcessor = bufferProcessor;
        this.logger = logger;
        this.bufferBytes = bufferBytes;

        this.queue = shouldBlock
                ? new BlockingSendReceive<>(PREPARE_COUNT - 1, TIMEOUT_MS)
                : new SemiBlockingSendReceive<>(PREPARE_COUNT - 1, TIMEOUT_MS);

        preparedBuffers = new ByteBuffer[PREPARE_COUNT];

        for (int i = 0; i < PREPARE_COUNT; i++) {
            preparedBuffers[i] = BufferUtils.createByteBuffer(bufferBytes);
        }

        this.loopEnabled = loopEnabled && song.loopConfig().enabled();
        loopCount = song.loopConfig().loopCount();
    }

    public static int getByteSize(AudioFormat format, float seconds) {
        return (int) (seconds * format.getSampleSizeInBits() / 8.0F * format.getChannels() * format.getSampleRate());
    }

    public static int getFrameCount(AudioFormat format, int byteSize) {
        return (int) (byteSize / ((format.getSampleSizeInBits() / 8.0F) * format.getChannels()));
    }

    public static float getSeconds(AudioFormat format, int frameCount) {
        return frameCount / format.getSampleRate();
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public @Nullable ByteBuffer read(int size) {
        synchronized (this) {
            if (ended && queue.isEmpty()) {
                logger.debug("Song has ended");
                return null;
            }

            if (queue.isEmpty() && (producer == null || !producer.isAlive())) {
                logger.error("No producer active");
                return null;
            }
        }

        ByteBuffer buf;

        try {
            buf = queue.take();
        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for producer, ending...");
            return null;
        }

        if (buf == null) {
            logger.debug("No more elements in the queue, song will be stopped");
        }

        return buf;
    }

    public CompletableFuture<Void> startProducer(int initialSegments) {
        logger.debug("Starting a new producer when the old one has shut down...");

        return whenThreadsShutdown().thenCompose(nil -> startNewProducer(initialSegments));
    }

    private synchronized CompletableFuture<Void> startNewProducer(final int segments) {
        if (segments > PREPARE_COUNT) {
            throw new IllegalStateException("Too much segments requested");
        }

        soundMixer.reset();
        this.reset();

        CompletableFuture<Void> future = new CompletableFuture<>();

        var crashed = new AtomicBoolean(false);

        final Thread processor = Thread.ofVirtual().name("Song Audio Preprocessor").start(() -> {
            logger.debug("New producer #{} has started", Thread.currentThread().threadId());

            boolean active = true;

            while (active && !Thread.currentThread().isInterrupted()) {
                logger.debug("Preparing next segment ({} queued, producer #{})", queue.size(), Thread.currentThread().threadId());

                active = prepare(bufferBytes);

                logger.debug("Segment prepared ({} queued, producer #{})", queue.size(), Thread.currentThread().threadId());

                if (!active) {
                    logger.debug("Producer #{} is done", Thread.currentThread().threadId());
                }

                crashed.set(false);

                if (queue.size() >= segments && !future.isDone()) {
                    future.complete(null);
                }
            }

            logger.debug("Song audio producer shutdown: {}", Thread.currentThread());
        });

        watchdog = Thread.ofVirtual().name("Song Audio Preprocessor Watchdog").start(() -> {
            while (processor.isAlive()) {
                if (crashed.getAndSet(true)) {
                    logger.debug("Song audio preprocessor seems to have crashed or is dead-locked, shutting it down...");
                    processor.interrupt();
                    break;
                }

                try {
                    //noinspection BusyWait
                    Thread.sleep(TIMEOUT_MS);
                } catch (InterruptedException ignored) {
                    logger.debug("Song watchdog got interrupted for producer #{}", processor.threadId());
                    break;
                }
            }

            logger.debug("Song audio watchdog for producer #{} shutdown: {}", processor.threadId(), Thread.currentThread());
        });

        producer = processor;

        return future;
    }

    private boolean prepare(int size) {
        synchronized (this) {
            if (ended) {
                // cannot prepare any more elements
                logger.debug("Song has already ended (producer #{})", Thread.currentThread().threadId());
                return false;
            }
        }

        final int frameCount = getFrameCount(format, size);
        float bufferSeconds = getSeconds(format, frameCount - frameOffset);

        if (first) {
            bufferSeconds += soundMixer.getCompressorLookAheadSeconds();
            first = false;
        }

        final int songDurationTicks = song.durationTicks();

        final int durationTicks = song.tempo().durationTicks(tick, bufferSeconds);
        int endTick = min(tick + durationTicks, songDurationTicks + 1);

        if (endTick > songDurationTicks) {
            // last segment of the song
            LoopConfig loop = song.loopConfig();

            if (loopEnabled && (loop.infinite() || loopCount > 0)) {
                loopCount = max(0, loopCount - 1);

                int interval = max(2, min(8, song.signature())) * 4;
                int adjustedEndTick = songDurationTicks + interval - (songDurationTicks % interval);
                int ticksUntilAdjustedEnd = adjustedEndTick - tick - 1;

                float endSeconds = song.tempo().durationSeconds(tick, ticksUntilAdjustedEnd);

                frameOffset = songMixer.mixTicks(tick, adjustedEndTick, this.frameOffset);

                frameOffset %= soundMixer.getBufferFrames();

                tick = loop.loopStartTick();

                float remainingSeconds = max(0.f, bufferSeconds - endSeconds);
                int remainingTicks = song.tempo().durationTicks(tick, remainingSeconds);

                endTick = min(tick + remainingTicks, songDurationTicks + 1);
            }
        }

        if (tick >= endTick) {
            if (soundMixer.isDone()) {
                synchronized (this) {
                    // song ended
                    ended = true;
                }

                logger.debug("Song is done (producer #{})", Thread.currentThread().threadId());
                return false;
            }
        } else {
            onUpdate.run();
            frameOffset = max(0, songMixer.mixTicks(tick, endTick, frameOffset) - soundMixer.getBufferFrames());
        }

        ByteBuffer buf = bufferProcessor.process(frameCount, soundMixer.getRootScope());

        ByteBuffer preparedBuffer;

        synchronized (this) {
            if (Thread.currentThread().isInterrupted()) {
                logger.debug("Song producer #{} was interrupted while processing", Thread.currentThread().threadId());
                return false;
            }

            preparedBuffer = preparedBuffers[prepareIdx];
            copyBuffer(buf, preparedBuffer);

            soundMixer.advanceBuffer();
            tick = endTick;

            prepareIdx = (prepareIdx + 1) % preparedBuffers.length;
        }

        try {
            if (!queue.offer(preparedBuffer)) {
                logger.debug("Song audio queue didn't get polled for the specified timeout. Shutting down producer...");
                return false;
            }
        } catch (InterruptedException ignored) {
            // assume sound was stopped
            logger.debug("Song producer #{} was interrupted while waiting for the queue to be polled", Thread.currentThread().threadId());
            return false;
        }

        return true;
    }

    private void copyBuffer(ByteBuffer src, ByteBuffer dst) {
        dst.position(0);
        dst.limit(dst.capacity());

        if (src.remaining() > dst.remaining()) {
            throw new IllegalStateException("Src buffer is bigger than dst buffer");
        }

        dst.put(src);

        dst.flip();
    }

    @Override
    public synchronized void close() {
        logger.debug("Closing song audio stream...");

        stopThreads();
    }

    public CompletableFuture<Void> setTick(int tick) {
        logger.debug("Setting playback tick when the old producer has shut down...");

        return whenThreadsShutdown().thenRun(() -> {
            synchronized (this) {
                reset();
                this.tick = max(0, tick);
            }
        });
    }

    public synchronized void reset() {
        prepareIdx = 0;
        first = true;
        frameOffset = 0;
        queue.clear();
        soundMixer.reset();
    }

    private synchronized void stopThreads() {
        if (producer != null && producer.isAlive()) {
            logger.debug("Stopping producer #{}", producer.threadId());
            producer.interrupt();
        }

        if (watchdog != null && watchdog.isAlive()) {
            logger.debug("Stopping watchdog #{}", watchdog.threadId());
            watchdog.interrupt();
        }
    }

    private synchronized CompletableFuture<Void> whenThreadsShutdown() {
        if ((producer == null || !producer.isAlive()) && (watchdog == null || !watchdog.isAlive())) {
            return CompletableFuture.completedFuture(null);
        }

        stopThreads();

        return CompletableFuture.runAsync(this::waitForThreads);
    }

    private synchronized void waitForThreads() {
        if (producer != null && producer.isAlive()) {
            try {
                logger.debug("Waiting for previous producer to shut down...");

                producer.join();

                logger.debug("Previous producer shut down");
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for previous producer to shut down", e);
            }
        }

        if (watchdog != null && watchdog.isAlive()) {
            try {
                logger.debug("Waiting for previous watchdog to shut down...");

                watchdog.join();

                logger.debug("Previous watchdog shut down");
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for previous watchdog to shut down", e);
            }
        }
    }
}
