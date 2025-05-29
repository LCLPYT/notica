package work.lclpnet.notica.impl.mix;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.impl.NoteSampler;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.IntFunction;

import static java.lang.Math.*;

public class SoundMixer {

    private static final int BASE_BUFFER_COUNT = 4;
    private static final float MAX_SOUND_SECONDS = 16.f;

    @Getter
    private final AudioFormat format;
    private final NoteSampler noteSampler;
    private final Compressor compressor;
    private final int bufferSize;
    @Getter
    private final int bufferFrames;
    private final float[] sampleBuffer;
    private final ByteBuffer directBuffer;
    private final float[][] buffers;
    private int currentBuffer = 0;
    private int remainingBuffers = 0;

    public SoundMixer(AudioFormat format, NoteSampler noteSampler, final int bufferBytes) {
        this.format = format;
        this.noteSampler = noteSampler;

        if (format.getChannels() != 2) {
            throw new IllegalArgumentException("Implementation expects stereo audio format");
        }

        GainReduction gainReduction = createGainReduction(format);

        compressor = new Compressor(gainReduction);

        int sampleBytes = format.getSampleSizeInBits() / 8;
        int bufferSize = bufferBytes / sampleBytes;
        this.bufferSize = bufferSize;
        this.bufferFrames = bufferSize / format.getChannels();

        ByteOrder order = format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        final float bufferDurationSeconds = SongAudioStream.getSeconds(format, bufferFrames);
        final int extraBufferCount = (int) ceil(MAX_SOUND_SECONDS / bufferDurationSeconds);
        final int bufferCount = BASE_BUFFER_COUNT + extraBufferCount;

        sampleBuffer = new float[extraBufferCount * bufferSize];
        directBuffer = BufferUtils.createByteBuffer(bufferBytes).order(order);

        buffers = new float[bufferCount][0];

        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new float[bufferSize];
        }
    }

    private @NotNull GainReduction createGainReduction(AudioFormat format) {
        int sampleRate = (int) format.getSampleRate();
        float lookaheadSec = 0.01f;
        float thresholdDb = (float) (log10(Short.MAX_VALUE / (Short.MAX_VALUE + 1f)) * 20f);
        float attackSec = 0.02f;
        float releaseSec = 0.2f;
        float holdSec = 0.002f;
        float ratio = Float.POSITIVE_INFINITY;
        float crestReleaseSec = 0.2f;
        float adaptationSec = 2f;

        return new GainReduction(sampleRate, lookaheadSec, thresholdDb, attackSec, releaseSec, holdSec, ratio, crestReleaseSec, adaptationSec);
    }

    public float getCompressorLookAheadSeconds() {
        return compressor.gainReduction().getLookaheadSamples() / format.getSampleRate();
    }

    /**
     * Mix the sound sample of a {@link Note} into the sound ring buffer with a given frameOffset.
     * If the sound sample is longer than the current buffer element capacity, the rest of it is put into the
     * following buffers of the ring buffer.
     * If the sample is longer than the total ring buffer capacity, that sound cannot be added.
     * Assumes that all ring buffer elements have an equal size.
     * Also assumes stereo channels.
     * @param note The {@link Note} defining the instrument, volume, pitch and panning.
     * @param volume The volume to play the sample at. Note velocity (volume) is multiplied with this value.
     * @param layerPanning The unnormalized stereo panning of the note layer; between [0, 200], where 0 is the center.
     * @param frameOffset The sample offset inside the current buffer. Is used to add sounds at specific timestamps.
     * @return True if the sound was successfully mixed into the ring buffer.
     * False if the sound was too long or if no sample exists fot the given {@link Note} instrument.
     */
    public boolean putSound(Note note, float volume, short layerPanning, int frameOffset) {
        final int frameCount = bindSample(note, volume, layerPanning);

        return mixSample(frameOffset, frameCount);
    }

    public int bindSample(Note note, float volume, short layerPanning) {
        return noteSampler.sample(note, volume, layerPanning, sampleBuffer);
    }

    public boolean mixSample(int frameOffset, int frameCount) {
        if (frameCount < 0) {
            return false;
        }

        final int bufferIdx = currentBuffer;

        if (notEnoughSpace(frameOffset, frameCount, bufferIdx)) {
            return false;
        }

        mixSample(sampleBuffer, frameCount, bufferIdx, frameOffset);

        return true;
    }

    private boolean notEnoughSpace(int frameOffset, int frameCount, int bufferIdx) {
        int bufferSpan = bufferSpan(frameOffset, frameCount);

        int distanceToCurrent = (bufferIdx - currentBuffer + buffers.length) % buffers.length;
        int available = buffers.length - distanceToCurrent;

        return bufferSpan > available;
    }

    private int bufferSpan(int frameOffset, int frameCount) {
        final int channels = 2;
        final int sampleCount = frameCount * channels;
        final int sampleOffset = frameOffset * channels;

        int startBuffer = (sampleOffset / bufferSize);
        int endBuffer = ((sampleOffset + sampleCount - 1) / bufferSize);

        return endBuffer - startBuffer + 1;
    }

    @VisibleForTesting
    void mixSample(float[] sample, final int frameCount, int bufferIdx, final int totalFrameOffset) {
        final int bufferOffset = totalFrameOffset / bufferFrames;
        final int frameOffset = totalFrameOffset % bufferFrames;

        // figure out first buffer index
        bufferIdx = (bufferIdx + bufferOffset) % buffers.length;

        // mix first part with offset into the first buffer
        int writtenFrames = mixOffset(sample, frameCount, bufferIdx, frameOffset);

        // mix the rest into the following buffers
        mixRest(sample, frameCount, bufferIdx, totalFrameOffset, writtenFrames);
    }

    private int mixOffset(float[] sample, int frameCount, int bufferIdx, int frameOffset) {
        final int len = max(0, min(frameCount, bufferFrames - frameOffset));
        float[] buffer = buffers[bufferIdx];

        // left
        for (int i = 0; i < len; i++) {
            buffer[frameOffset + i] += sample[i];
        }

        // right
        for (int i = 0; i < len; i++) {
            buffer[bufferFrames + frameOffset + i] += sample[frameCount + i];
        }

        return len;
    }

    private void mixRest(float[] sample, int frameCount, int bufferIdx, int totalFrameOffset, final int writtenFrames) {
        final int span = bufferSpan(totalFrameOffset, frameCount);

        remainingBuffers = span;

        final int restBufferSpan = span - 1;

        for (int i = 1; i <= restBufferSpan; i++) {
            int writtenSoFar = writtenFrames + (i - 1) * bufferFrames;
            int remainingFrames = frameCount - writtenSoFar;
            int len = max(0, min(bufferFrames, remainingFrames));

            int idx = (bufferIdx + i) % buffers.length;
            float[] buffer = buffers[idx];

            // left
            for (int j = 0; j < len; j++) {
                buffer[j] += sample[j + writtenSoFar];
            }

            // right
            for (int j = 0; j < len; j++) {
                buffer[bufferFrames + j] += sample[frameCount + j + writtenSoFar];
            }
        }
    }

    public static ByteBuffer changePitch(ByteBuffer input, float pitch, AudioFormat format, IntFunction<ByteBuffer> outputFactory) {
        if (pitch == 1.0) {
            return input;
        }

        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int sampleBytes = format.getSampleSizeInBits() / 8;  // support non-multiples of 8?

        int baseFrameCount = input.limit() / frameSize;
        int sampleFrameCount = (int) (baseFrameCount / pitch);

        ByteBuffer output = outputFactory.apply(sampleFrameCount).order(ByteOrder.LITTLE_ENDIAN);

        for (int frame = 0; frame < sampleFrameCount; frame++) {
            double exactIdx = frame * pitch;
            int idx = (int) exactIdx;
            double delta = exactIdx - idx;

            if (idx + 1 >= baseFrameCount) break;

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

    public static void changeVolume(ByteBuffer samples, float volume) {
        if (volume == 1.0) return;

        int len = samples.limit();

        samples.position(0);

        for (int i = 0; i < len; i++) {
            short sample = samples.getShort();

            sample = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, round(sample * volume)));

            samples.putShort(sample);
        }
    }

    public ByteBuffer applyCompressor(final int frameCount) {
        float[] samples = buffers[currentBuffer];
        float[] next = buffers[(currentBuffer + 1) % buffers.length];

        // floating point samples might be outside the playable 16-bit range
        // apply dynamic-range-compression in order to make everything playable
        // this reduces audio over-amplification and clipping significantly
        compressor.process(frameCount, samples, next, directBuffer);

        return directBuffer;
    }

    @SuppressWarnings("SameParameterValue")
    @VisibleForTesting
    ByteBuffer applyClamping(final int frameCount) {
        ByteBuffer buf = BufferUtils.createByteBuffer(frameCount * 4);
        buf.position(0);

        final int bufCount = (int) Math.ceil((float) frameCount / bufferFrames);

        for (int i = 0; i < bufCount; i++) {
            float[] samples = buffers[i];
            final int len = max(0, min(bufferFrames, frameCount - i * bufferFrames));

            for (int j = 0; j < len; j++) {
                float vl = samples[j];
                float vr = samples[bufferFrames + j];

                short ql = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, vl * Short.MAX_VALUE));
                short qr = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, vr * Short.MAX_VALUE));

                buf.putShort(ql);
                buf.putShort(qr);
            }
        }

        buf.flip();

        return buf;
    }

    public void reset() {
        currentBuffer = 0;

        for (float[] buffer : buffers) {
            Arrays.fill(buffer, 0f);
        }

        compressor.reset();
    }

    public void advanceBuffer() {
        Arrays.fill(buffers[currentBuffer], 0f);

        currentBuffer = (currentBuffer + 1) % buffers.length;
        remainingBuffers = max(0, remainingBuffers - 1);
    }

    public boolean isDone() {
        return remainingBuffers <= 0;
    }
}
