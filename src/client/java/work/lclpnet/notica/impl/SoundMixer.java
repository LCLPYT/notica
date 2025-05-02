package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.math.random.Random;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.data.Song;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class SoundMixer {

    private static int
            RESERVE_BUFFERS = 2,
            SECTION_LENGTH_MS = 5000;

    private final Song song;
    private final Channel channel;
    private final AudioFormat format;
    private final UnifiedSoundLoader soundLoader;
    private final SoundSampleManager sampleManager;
    private final ByteBuffer[] sectionBuffers = new ByteBuffer[RESERVE_BUFFERS + 2];  // current + upNext + reserve

    public SoundMixer(Song song, Channel channel, AudioFormat format, UnifiedSoundLoader soundLoader,
                      InstrumentSoundProvider soundProvider, SoundManager soundManager,
                      DirectSoundManager directSoundManager) {
        this.channel = channel;
        this.format = format;
        this.soundLoader = soundLoader;
        this.song = song;
        this.sampleManager = new SoundSampleManager(song.instruments(), soundProvider, Random.create(42), soundManager, directSoundManager, soundLoader);
    }

    public void start(int startTick, float volume) {
        preloadSounds().thenRun(() -> startBuffering(startTick, volume));
    }

    private void startBuffering(int startTick, float volume) {
        // TODO begin buffering
    }

    public CompletableFuture<Void> preloadSounds() {
        return CompletableFuture.runAsync(sampleManager::loadAll);
    }

    private ByteBuffer changePitch(ByteBuffer input, float pitch) {
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int sampleBytes = format.getSampleSizeInBits() / 8;  // support non-multiples of 8?

        int frameIn = input.limit() / frameSize;
        int frameOut = (int) (frameIn / pitch);

        var output = ByteBuffer.allocateDirect(frameOut * frameSize).order(ByteOrder.LITTLE_ENDIAN);

        for (int frame = 0; frame < frameOut; frame++) {
            double exactIdx = frame * pitch;
            int idx = (int) exactIdx;
            double delta = exactIdx - idx;

            if (idx + 1 >= frameIn) break;

            for (int channel = 0; channel < channels; channel++) {
                int leftIdx = (idx * channels + channel) * sampleBytes;
                int rightIdx = ((idx + 1) * channels + channel) * sampleBytes;

                short leftSample = input.getShort(leftIdx);
                short rightSample = input.getShort(rightIdx);

                short interpolatedSample = (short) ((1.d - delta) * leftSample + delta * rightSample);
                output.putShort(interpolatedSample);
            }
        }

        output.flip();

        return output;
    }

    private ByteBuffer mix(ByteBuffer x, ByteBuffer y) {
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int frameX = x.remaining() / frameSize;
        int frameY = y.remaining() / frameSize;

        int frames = max(frameX, frameY);
        var dst = BufferUtils.createByteBuffer(frames * frameSize);

        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                short sx = frame < frameX ? x.getShort() : 0;
                short sy = frame < frameY ? y.getShort() : 0;
                short mixed = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, sx + sy));

                dst.putShort(mixed);
            }
        }

        return dst;
    }
}
