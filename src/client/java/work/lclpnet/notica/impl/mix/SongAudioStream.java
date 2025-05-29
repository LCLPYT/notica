package work.lclpnet.notica.impl.mix;

import net.minecraft.client.sound.AudioStream;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.LoopConfig;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SongMixer;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class SongAudioStream implements AudioStream {

    private static final int PREPARE_COUNT = 5;

    private final AudioFormat format;
    private final Song song;
    private final SoundMixer soundMixer;
    private final SongMixer songMixer;
    private final ByteBuffer[] preparedBuffers;
    private final int bufferBytes;
    private final Object prepareLock = new Object[0];

    private Runnable onUpdate = () -> {};
    private boolean first = true;
    private boolean ended = false;
    private int tick = 0;
    private int prepared = 0;
    private int prepareStart = 0;
    private int frameOffset = 0;
    private int loopCount;

    public SongAudioStream(AudioFormat format, SoundMixer soundMixer, SongMixer songMixer, Song song, int bufferBytes) {
        this.format = format;
        this.soundMixer = soundMixer;
        this.songMixer = songMixer;
        this.song = song;
        this.bufferBytes = bufferBytes;

        preparedBuffers = new ByteBuffer[PREPARE_COUNT];

        for (int i = 0; i < PREPARE_COUNT; i++) {
            preparedBuffers[i] = BufferUtils.createByteBuffer(bufferBytes);
        }

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

    public int getBufferBytes() {
        return bufferBytes;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        boolean needsProcessing = false;

        synchronized (this) {
            if (prepared <= 0) {
                if (ended) {
                    return null;
                }

                needsProcessing = true;
            }
        }

        if (needsProcessing) {
            synchronized (prepareLock) {

                // check if another thread prepared something in the meantime
                synchronized (this) {
                    if (prepared <= 0) {
                        if (ended) {
                            return null;
                        }
                    }
                }

                // prepare without locking this to prevent deadlock
                _prepare(size);
            }
        }

        ByteBuffer buf;

        synchronized (this) {
            if (prepared <= 0) {
                throw new IllegalStateException("No buffer is prepared, likely a race-condition");
            }

            buf = preparedBuffers[prepareStart];

            prepared--;
            prepareStart = (prepareStart + 1) % PREPARE_COUNT;
        }

        // prepare next async
        Thread.startVirtualThread(() -> prepare(size));

        return buf;
    }

    public void prepare(int size) {
        synchronized (prepareLock) {
            int count = max(1, size / bufferBytes);
            int remaining = size;

            for (int i = 0; i < count; i++) {
                int len = min(remaining, bufferBytes);

                _prepare(len);

                remaining -= len;
            }
        }
    }

    private void _prepare(int size) {
        synchronized (this) {
            if (prepared >= PREPARE_COUNT || ended) return;  // cannot prepare any more elements
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

            if (loop.enabled() && (loop.infinite() || loopCount > 0)) {
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

                return;
            }
        } else {
            onUpdate.run();
            frameOffset = max(0, songMixer.mixTicks(tick, endTick, frameOffset) - soundMixer.getBufferFrames());
        }

        ByteBuffer buf = soundMixer.applyCompressor(frameCount);

        synchronized (this) {
            int prepareIdx = (prepareStart + prepared) % PREPARE_COUNT;

            copyBuffer(buf, preparedBuffers[prepareIdx]);

            soundMixer.advanceBuffer();
            tick = endTick;

            prepared++;
        }
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
    public void close() {}

    public synchronized void setTick(int tick) {
        this.tick = max(0, tick);

        reset();
    }

    public synchronized void reset() {
        prepared = 0;
        prepareStart = 0;
        first = true;
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }
}
