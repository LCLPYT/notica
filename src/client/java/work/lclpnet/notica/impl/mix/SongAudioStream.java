package work.lclpnet.notica.impl.mix;

import net.minecraft.client.sound.AudioStream;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.Song;

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

    private boolean ended = false;
    private int tick = 0;
    private int prepared = 0;
    private int prepareStart = 0;

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

        prepareAsync(size);

        return buf;
    }

    public Thread prepareAsync(int size) {
        return Thread.startVirtualThread(() -> {
            int count = max(1, size / bufferBytes);
            int remaining = size;

            for (int i = 0; i < count; i++) {
                int len = min(remaining, bufferBytes);

                prepare(len);

                remaining -= len;
            }
        });
    }

    public void prepare(int size) {
        synchronized (prepareLock) {
            _prepare(size);
        }
    }

    private void _prepare(int size) {
        synchronized (this) {
            if (prepared >= PREPARE_COUNT || ended) return;  // cannot prepare any more elements
        }

        final int frameCount = getFrameCount(format, size);
        float seconds = getSeconds(format, frameCount);

        int durationTicks = song.tempo().durationTicks(tick, seconds);
        int endTick = min(tick + durationTicks, song.durationTicks());

        if (endTick <= tick) {
            synchronized (this) {
                // song ended
                ended = true;
            }

            return;
        }

        songMixer.mixTicks(tick, endTick, 0);

        // TODO make advanceBuffer internal and call it when the position of the buffer exceeds it's limit
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

    public void setTick(int tick) {
        this.tick = tick;
    }
}
