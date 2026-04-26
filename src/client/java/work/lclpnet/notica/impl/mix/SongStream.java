package work.lclpnet.notica.impl.mix;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import work.lclpnet.notica.api.data.LoopConfig;
import work.lclpnet.notica.api.data.LoopOverride;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.ds.BlockingSendReceive;
import work.lclpnet.notica.impl.ds.SemiBlockingSendReceive;
import work.lclpnet.notica.impl.ds.SendReceive;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.*;

/**
 * Produces a continuous stream of pre-rendered PCM audio buffers for a {@link Song}.
 * <p>
 * A background <em>producer</em> virtual thread advances the song tick-by-tick, mixes audio
 * via {@link SongMixer} and {@link SoundMixer}, and pushes finished {@link ByteBuffer}s into a
 * bounded {@link SendReceive} queue. A separate <em>watchdog</em> virtual thread monitors the
 * producer and interrupts it if it stalls for longer than {@value TIMEOUT_MS} ms.
 * <p>
 * Callers consume buffers by polling {@link #nextBuffer()}, which either blocks or returns
 * {@code null} immediately depending on the queue implementation chosen at construction time
 * (controlled by the {@code shouldBlock} flag).
 * <p>
 * Looping is supported through {@link LoopConfig}: when the song end is reached and looping is
 * enabled, the producer seamlessly wraps back to the configured loop-start tick.
 * <p>
 * This class is thread-safe: producer/watchdog threads and the consumer thread synchronize on
 * {@code this} for shared state. {@link #close()} stops both background threads.
 */
public class SongStream implements AutoCloseable {

    /** Number of pre-allocated {@link ByteBuffer} slots shared between producer and consumer. */
    private static final int
            PREPARE_COUNT = 5,
            /** Timeout in milliseconds for queue offer operations and the watchdog interval. */
            TIMEOUT_MS = 10_000;

    private final AudioFormat format;
    private final Song song;
    private final SoundMixer soundMixer;
    private final SongMixer songMixer;
    private final BufferProcessor bufferProcessor;
    private final Logger logger;
    private final ByteBuffer[][] preparedBuffers;
    @Getter
    private final int bufferBytes;
    private final SendReceive<ByteBuffer[]> queue;
    private final LoopConfig loopConfig;
    private final int outputBufferCount;
    private final boolean mixToMono;

    private @Nullable Thread producer = null, watchdog = null;
    @Setter
    private Runnable onUpdate = () -> {};
    private boolean first = true;
    private boolean ended = false;
    private int tick = 0;
    private int prepareIdx = 0;
    private int frameOffset = 0;
    private int loopCount;

    /**
     * Creates a new {@link SongStream}.
     *
     * @param format          the PCM audio format used for all produced buffers
     * @param soundMixer      low-level mixer that renders individual note samples
     * @param songMixer       high-level mixer that advances song ticks and triggers notes
     * @param song            the song to stream
     * @param bufferProcessor post-processes rendered frames (e.g. applies compression/limiting)
     * @param logger          logger for debug and error output
     * @param bufferBytes     size in bytes of each individual audio buffer
     * @param loopOverride    overrides the song's built-in loop configuration if needed
     * @param shouldBlock     if {@code true}, {@link #nextBuffers()} blocks until a buffer is
     *                        ready; if {@code false}, it returns {@code null} when the queue is empty
     * @param outputBuffers   the amount of output buffers. One for stereo output. Two for outputting the stereo channels individually as mono audio.
     * @param mixToMono       Whether to mix stereo down to mono.
     */
    public SongStream(AudioFormat format, SoundMixer soundMixer, SongMixer songMixer, Song song,
                      BufferProcessor bufferProcessor, Logger logger, int bufferBytes, LoopOverride loopOverride,
                      boolean shouldBlock, int outputBuffers, boolean mixToMono) {
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

        if (outputBuffers <= 0) {
            throw new IllegalArgumentException("At least one output buffer is required");
        }

        if (bufferBytes % outputBuffers != 0) {
            throw new IllegalArgumentException("Invalid output buffer count: source buffer size of %s cannot be split evenly into %s parts".formatted(bufferBytes, outputBuffers));
        }

        int outputBufferSize = bufferBytes / outputBuffers;

        preparedBuffers = new ByteBuffer[PREPARE_COUNT][outputBuffers];

        for (int i = 0; i < PREPARE_COUNT; i++) {
            for (int j = 0; j < outputBuffers; j++) {
                preparedBuffers[i][j] = BufferUtils.createByteBuffer(outputBufferSize);
            }
        }

        this.outputBufferCount = outputBuffers;
        this.mixToMono = mixToMono;

        this.loopConfig = loopOverride.override(song.loopConfig());
        loopCount = loopConfig.loopCount();
    }

    /**
     * Returns the audio duration in seconds covered by a single buffer produced by this stream.
     *
     * @return buffer duration in seconds
     */
    public float getBufferSeconds() {
        return getSeconds(format, getFrameCount(format, bufferBytes));
    }

    /**
     * Calculates the byte size of a PCM buffer that covers the given duration.
     *
     * @param format  the audio format
     * @param seconds the desired duration in seconds
     * @return the number of bytes needed to hold that duration of audio
     */
    public static int getByteSize(AudioFormat format, float seconds) {
        return (int) (seconds * format.getSampleSizeInBits() / 8.0F * format.getChannels() * format.getSampleRate());
    }

    /**
     * Converts a byte size to the corresponding number of audio frames.
     *
     * @param format   the audio format
     * @param byteSize the buffer size in bytes
     * @return the number of frames that fit in the given byte size
     */
    public static int getFrameCount(AudioFormat format, int byteSize) {
        return (int) (byteSize / ((format.getSampleSizeInBits() / 8.0F) * format.getChannels()));
    }

    /**
     * Converts a frame count to its duration in seconds.
     *
     * @param format     the audio format
     * @param frameCount the number of frames
     * @return the duration in seconds
     */
    public static float getSeconds(AudioFormat format, int frameCount) {
        return frameCount / format.getSampleRate();
    }

    /**
     * Returns the next pre-rendered audio buffers, or {@code null} when the stream has ended or
     * no buffers are currently available.
     * <p>
     * Whether this call blocks depends on the queue implementation chosen at construction:
     * with {@code shouldBlock = true} it waits until a buffer arrives; with
     * {@code shouldBlock = false} it returns {@code null} immediately if the queue is empty.
     * A {@code null} return always signals that the caller should stop requesting further buffers.
     *
     * @return the next PCM buffer, or {@code null} if the stream has ended or no data is available
     */
    public @Nullable ByteBuffer[] nextBuffers() {
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

        ByteBuffer[] bufs;

        try {
            bufs = queue.take();
        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for producer, ending...");
            return null;
        }

        if (bufs == null) {
            logger.debug("No more elements in the queue, song will be stopped");
        }

        return bufs;
    }

    /**
     * Starts a new producer (and its watchdog) after any previously running threads have stopped.
     * <p>
     * The returned future completes once at least {@code initialSegments} buffers have been
     * queued and are ready for consumption, or immediately when the producer finishes — whichever
     * comes first. The caller can use this future to delay playback until enough audio is buffered.
     *
     * @param initialSegments the minimum number of buffers to pre-fill before completing the future;
     *                        must not exceed {@value PREPARE_COUNT}
     * @return a future that completes when the stream is ready for consumption
     */
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
                active = prepare(bufferBytes);

                if (!active) {
                    logger.debug("Producer #{} is done", Thread.currentThread().threadId());
                }

                crashed.set(false);

                if (queue.size() >= segments && !future.isDone()) {
                    future.complete(null);
                }
            }

            logger.debug("Song audio producer shutdown: {}", Thread.currentThread());

            future.complete(null);
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

    /**
     * Renders one audio buffer of {@code size} bytes and enqueues it for consumption.
     * <p>
     * The method advances the internal tick cursor by however many ticks fit into the buffer
     * duration. When the song end is reached and looping is enabled, it wraps back to
     * {@link LoopConfig#loopStartTick()}, pads the tail with silence up to the next bar
     * boundary (determined by the time signature), and fills the remainder of the buffer from
     * the loop start. When looping is disabled, the producer marks the stream as ended and
     * returns {@code false}.
     * <p>
     * The rendered PCM data is copied into one of the {@value PREPARE_COUNT} pre-allocated
     * {@link #preparedBuffers} slots (round-robin) to avoid per-buffer allocations, then offered
     * to the {@link #queue}. If the queue does not accept the buffer within the configured
     * timeout, the producer is considered stalled and {@code false} is returned.
     *
     * @param size the number of bytes to render; must match the capacity of the pre-allocated buffers
     * @return {@code true} if rendering and enqueuing succeeded and the producer should continue;
     *         {@code false} if the producer should stop (song ended, interrupted, or queue stalled)
     */
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
            if (loopConfig.enabled() && (loopConfig.infinite() || loopCount > 0)) {
                loopCount = max(0, loopCount - 1);

                int interval = clamp(song.signature(), 2, 8) * 4;
                int adjustedEndTick = songDurationTicks + interval - (songDurationTicks % interval);
                int ticksUntilAdjustedEnd = adjustedEndTick - tick - 1;

                float endSeconds = song.tempo().durationSeconds(tick, ticksUntilAdjustedEnd);

                frameOffset = songMixer.mixTicks(tick, adjustedEndTick, this.frameOffset);

                frameOffset %= soundMixer.getBufferFrames();

                tick = loopConfig.loopStartTick();

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

        ByteBuffer[] bufs = genOutputBuffers(frameCount);

        ByteBuffer[] preparedBuffer;

        synchronized (this) {
            if (Thread.currentThread().isInterrupted()) {
                logger.debug("Song producer #{} was interrupted while processing", Thread.currentThread().threadId());
                return false;
            }

            preparedBuffer = preparedBuffers[prepareIdx];

            for (int i = 0; i < outputBufferCount; i++) {
                copyBuffer(bufs[i], preparedBuffer[i]);
            }

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

    private ByteBuffer @NonNull [] genOutputBuffers(int frameCount) {
        // de-interleaved audio samples (channel blocks)
        float[] samples = bufferProcessor.process(frameCount, soundMixer.getRootScope());

        ByteBuffer[] bufs = new ByteBuffer[outputBufferCount];

        if (outputBufferCount >= 2) {
            for (int i = 0; i < outputBufferCount; i++) {
                bufs[i] = soundMixer.toChannelBytes(samples, frameCount, i);
            }

            return bufs;
        }

        if (mixToMono) {
            bufs[0] = soundMixer.toMonoPCM(samples, frameCount);
        } else {
            bufs[0] = soundMixer.toStereoPCM(samples, frameCount);
        }

        return bufs;
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

    /**
     * Closes this stream by interrupting the producer and watchdog threads.
     * After this call returns the stream should no longer be used.
     */
    @Override
    public synchronized void close() {
        logger.debug("Closing song audio stream...");

        stopThreads();
    }

    /**
     * Seeks to the given song tick, stopping any running producer first.
     * <p>
     * The returned future completes once the previous producer has fully shut down and the
     * tick has been applied. Call {@link #startProducer(int)} afterwards to resume streaming.
     *
     * @param tick the song tick to seek to; clamped to {@code 0} if negative
     * @return a future that completes when the seek is done
     */
    public CompletableFuture<Void> setTick(int tick) {
        logger.debug("Setting playback tick when the old producer has shut down...");

        return whenThreadsShutdown().thenRun(() -> {
            synchronized (this) {
                reset();
                this.tick = max(0, tick);
            }
        });
    }

    /**
     * Resets internal playback state to the beginning of the stream.
     * Clears the buffer queue, resets the prepare index, frame offset, and mixer state.
     * Does not stop any running producer thread — call {@link #close()} or wait for shutdown first.
     */
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
