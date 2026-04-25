package work.lclpnet.notica.impl.mix;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.impl.ClientMusicBackend;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    /**
     * A buffer for the processed audio, split by channels (de-interleaved).
     */
    private final float[] floatBuffer;
    private final ByteBuffer[] directBuffers;
    @Getter
    private final Scope rootScope;
    private final Scope[] workerScopes;
    private final int extraBufferCount;

    private int currentBuffer = 0;
    private int remainingBuffers = 0;

    public SoundMixer(AudioFormat format, NoteSampler noteSampler, final int bufferBytes, int scopeCount, int outputBuffers) {
        this.format = format;
        this.noteSampler = noteSampler;

        if (format.getChannels() != 2) {
            throw new IllegalArgumentException("Implementation expects stereo audio format");
        }

        GainReduction gainReduction = createGainReduction(format);

        compressor = new Compressor(gainReduction, format);

        int sampleBytes = format.getSampleSizeInBits() / 8;
        int bufferSize = bufferBytes / sampleBytes;
        this.bufferSize = bufferSize;
        this.bufferFrames = bufferSize / format.getChannels();

        ByteOrder order = format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        floatBuffer = new float[bufferSize];

        if (outputBuffers <= 0) {
            throw new IllegalArgumentException("Need at least one output channel");
        }

        if (bufferBytes % outputBuffers != 0) {
            throw new IllegalArgumentException("Invalid output buffer count: source buffer size of %s cannot be split evenly into %s parts".formatted(bufferBytes, outputBuffers));
        }

        ByteBuffer[] directBuffers = new ByteBuffer[outputBuffers];
        int directBufferSize = bufferBytes / outputBuffers;

        for (int i = 0; i < outputBuffers; i++) {
            directBuffers[i] = BufferUtils.createByteBuffer(directBufferSize).order(order);
        }

        this.directBuffers = directBuffers;

        final float bufferDurationSeconds = SongStream.getSeconds(format, bufferFrames);
        extraBufferCount = (int) ceil(MAX_SOUND_SECONDS / bufferDurationSeconds);
        final int bufferCount = BASE_BUFFER_COUNT + extraBufferCount;

        if (scopeCount > 1) {
            rootScope = new Scope(0, bufferCount, bufferSize);
            workerScopes = new Scope[scopeCount];

            for (int i = 0; i < scopeCount; i++) {
                workerScopes[i] = createScope();
            }
        } else {
            rootScope = new Scope(extraBufferCount, bufferCount, bufferSize);
            workerScopes = new Scope[0];
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

    private Scope createScope() {
        return new Scope(extraBufferCount, rootScope.getBufferCount(), bufferSize);
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
    public boolean putSound(Note note, float volume, short layerPanning, int frameOffset, Scope scope) {
        final int frameCount = bindSample(note, volume, layerPanning, scope);

        return mixSample(frameOffset, frameCount, scope);
    }

    public int bindSample(Note note, float volume, short layerPanning, Scope scope) {
        return noteSampler.sample(note, volume, layerPanning, scope.getSampleBuffer());
    }

    public boolean mixSample(int frameOffset, int frameCount, Scope scope) {
        if (frameCount <= 0) {
            return false;
        }

        if (notEnoughSpace(frameOffset, frameCount, scope)) {
            return false;
        }

        mixSample(scope.getSampleBuffer(), frameCount, frameOffset, scope);

        return true;
    }

    @VisibleForTesting
    boolean notEnoughSpace(int frameOffset, int frameCount, Scope scope) {
        int bufferSpan = bufferSpan(frameOffset, frameCount);

        return bufferSpan > scope.getBufferCount();
    }

    @VisibleForTesting
    int bufferSpan(int frameOffset, int frameCount) {
        final int channels = 2;
        final int sampleCount = frameCount * channels;
        final int sampleOffset = frameOffset * channels;

        int startBuffer = (sampleOffset / bufferSize);
        int endBuffer = ((sampleOffset + sampleCount - 1) / bufferSize);

        return endBuffer - startBuffer + 1;
    }

    @VisibleForTesting
    void mixSample(float[] sample, final int frameCount, final int totalFrameOffset, Scope scope) {
        final int bufferOffset = totalFrameOffset / bufferFrames;
        final int frameOffset = totalFrameOffset % bufferFrames;

        // figure out first buffer index
        int bufferIdx = (currentBuffer + bufferOffset) % scope.getBufferCount();

        // mix first part with offset into the first buffer
        int writtenFrames = mixOffset(sample, frameCount, bufferIdx, frameOffset, scope);

        // mix the rest into the following buffers
        mixRest(sample, frameCount, bufferIdx, writtenFrames, scope);
    }

    private int mixOffset(float[] sample, int frameCount, int bufferIdx, int frameOffset, Scope scope) {
        final int len = clamp(frameCount, 0, bufferFrames - frameOffset);
        float[] buffer = scope.getBuffer(bufferIdx);

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

    private void mixRest(float[] sample, int frameCount, int bufferIdx, final int writtenFrames, Scope scope) {
        int remain = frameCount - writtenFrames;
        final int span = bufferSpan(0, remain);

        synchronized (this) {
            remainingBuffers = max(remainingBuffers, span + 1);
        }

        for (int i = 1; i <= span; i++) {
            int writtenSoFar = writtenFrames + (i - 1) * bufferFrames;
            int remainingFrames = frameCount - writtenSoFar;
            int len = clamp(bufferFrames, 0, remainingFrames);

            int idx = (bufferIdx + i) % scope.getBufferCount();
            float[] buffer = scope.getBuffer(idx);

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

    public float[] applyCompressor(final int frameCount, Scope scope) {
        float[] samples = scope.getBuffer(currentBuffer);
        float[] next = scope.getBuffer((currentBuffer + 1) % scope.getBufferCount());

        // floating point samples might be outside the playable 16-bit range
        // apply dynamic-range-compression in order to make everything playable
        // this reduces audio over-amplification and clipping significantly
        compressor.process(frameCount, samples, next, floatBuffer);

        return floatBuffer;
    }

    public float[] getCurrentBuffer(Scope scope) {
        return scope.getBuffer(currentBuffer);
    }

    /**
     * Converts a stereo sound in de-interleaved float format to interleaved byte PCM format.
     * @param frameCount The number frames to write.
     * @param samples The de-interleaved stereo float samples.
     * @return The interleaved byte PCM samples. Doesn't allocate a new buffer but re-uses a single buffer of the mixer instance.
     */
    public ByteBuffer toStereoPCM(float[] samples, int frameCount) {
        ByteBuffer directBuffer = directBuffers[0];

        directBuffer.position(0);
        directBuffer.limit(directBuffer.capacity());

        // re-interleave
        UnifiedSoundLoader.toInterleavedBytes(samples, frameCount, directBuffer, format);

        directBuffer.flip();

        return directBuffer;
    }

    public ByteBuffer toChannelBytes(float[] samples, int frameCount, int channel) {
        if (channel >= directBuffers.length) {
            throw new IllegalArgumentException("Not enough output buffers allocated (tried to get channel %s)".formatted(channel));
        }

        ByteBuffer directBuffer = directBuffers[channel];

        directBuffer.position(0);
        directBuffer.limit(directBuffer.capacity());

        UnifiedSoundLoader.toChannelBytes(samples, frameCount, directBuffer, ClientMusicBackend.getMonoFormat(format), channel);

        directBuffer.flip();

        return directBuffer;
    }

    public void reset() {
        currentBuffer = 0;

        rootScope.reset();

        compressor.reset();
    }

    public void advanceBuffer() {
        rootScope.resetBuffer(currentBuffer);

        for (Scope workerScope : workerScopes) {
            workerScope.resetBuffer(currentBuffer);
        }

        currentBuffer = (currentBuffer + 1) % rootScope.getBufferCount();
        remainingBuffers = max(0, remainingBuffers - 1);
    }

    public synchronized boolean isDone() {
        return remainingBuffers <= 0;
    }

    public Scope getWorkerScope(int i) {
        if (workerScopes.length == 0 && i == 0) {
            return rootScope;
        }

        return workerScopes[i];
    }

    public void combineScopes() {
        if (workerScopes.length == 0) return;

        var rootScope = this.rootScope;

        rootScope.copy(workerScopes[0]);

        for (int i = 1; i < workerScopes.length; i++) {
            rootScope.add(workerScopes[i]);
        }
    }
}
