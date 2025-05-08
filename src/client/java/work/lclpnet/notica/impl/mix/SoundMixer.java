package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.NoteHelper;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

import static java.lang.Math.*;

public class SoundMixer {

    private static final int RESERVE_BUFFERS = 2;
    public static final int SECTION_LENGTH_MS = 5000;  // 5000 ~ 1 MB per buffer

    private final Song song;
    private final AudioFormat format;
    private final SoundSampleManager sampleManager;
    private final StereoMode stereoMode;
    private final Compressor compressor;
    private final ByteBuffer[] buffers = new ByteBuffer[RESERVE_BUFFERS + 2];  // current + upNext + reserve
    private final float[][] internalBuffers = new float[buffers.length][0];

    public SoundMixer(Song song, AudioFormat format, SoundSampleManager sampleManager, StereoMode stereoMode) {
        this.format = format;
        this.song = song;
        this.sampleManager = sampleManager;
        this.stereoMode = stereoMode;

        if (format.getChannels() != 2) {
            throw new IllegalArgumentException("Implementation expects stereo audio format");
        }

        GainReduction gainReduction = createGainReduction(format);

        compressor = new Compressor(gainReduction);

        int sectionSampleCount = (int) ceil(SECTION_LENGTH_MS * 0.001f * format.getSampleRate() * format.getChannels());
        int sampleBytes = format.getSampleSizeInBits() / 8;
        int sectionSampleBytes = sectionSampleCount * sampleBytes;
        ByteOrder order = format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = BufferUtils.createByteBuffer(sectionSampleBytes).order(order);
            internalBuffers[i] = new float[sectionSampleCount];
        }
    }

    private @NotNull GainReduction createGainReduction(AudioFormat format) {
        int sampleRate = (int) format.getSampleRate();
        float lookaheadSec = 0.02f;
        float thresholdDb = (float) (log10(Short.MAX_VALUE / (Short.MAX_VALUE + 1f)) * 20f);
        float attackSec = 0.02f;
        float releaseSec = 0.02f;
        float holdSec = 0.002f;
        float ratio = Float.POSITIVE_INFINITY;
        float crestReleaseSec = 0.2f;
        float adaptationSec = 2f;

        return new GainReduction(sampleRate, lookaheadSec, thresholdDb, attackSec, releaseSec, holdSec, ratio, crestReleaseSec, adaptationSec);
    }

    public AudioFormat getFormat() {
        return format;
    }

    public CompletableFuture<Void> preloadSounds() {
        return CompletableFuture.runAsync(sampleManager::loadAll);
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
     * @param currentBuffer The index of the current {@link ByteBuffer} inside the ring buffer.
     * @param frameOffset The sample offset inside the current buffer. Is used to add sounds at specific timestamps.
     * @return True if the sound was successfully mixed into the ring buffer.
     * False if the sound was too long or if no sample exists fot the given {@link Note} instrument.
     */
    public boolean putSound(Note note, float volume, short layerPanning, int currentBuffer, int frameOffset) {
        ByteBuffer sample = sampleManager.getSample(note.instrument());

        if (sample == null) {
            return false;
        }

        float pitch = getPitch(note);
        volume *= note.velocity() * 1e-2f;
        float panning = NoteHelper.normalizePanning(layerPanning, note.panning());  // [-1, 1], 0=center

        int channels = 2;
        int sampleBytes = format.getSampleSizeInBits() / 8;  // support non-multiples of 8?
        int frameSize = format.getFrameSize();
        int baseFrameCount = sample.limit() / frameSize;
        int sampleFrameCount = (int) (baseFrameCount / pitch);

        // check if sample can fit into the buffer
        int bufferFrameCapacity = internalBuffers[currentBuffer].length / frameSize * sampleBytes;
        int availableBuffers = internalBuffers.length - 1;
        int currentFrameCapacity = bufferFrameCapacity - frameOffset;
        int totalFrameCapacity = currentFrameCapacity + availableBuffers * bufferFrameCapacity;

        if (sampleFrameCount >= totalFrameCapacity) {
            return false;
        }

        // calculate panning
        float leftPanning;
        float rightPanning;

        if (stereoMode == StereoMode.SPATIAL) {
            // mimic vanilla behavior:
            // if there is panning, mute the other channel and apply linear attenuation with 16 block range.
            // a panning of 1 means 2 blocks from the nbs specification
            if (panning < 0) {
                leftPanning = 1.f - (-panning / 8f);
                rightPanning = 0;
            } else if (panning > 0) {
                leftPanning = 0;
                rightPanning = 1.f - (panning / 8f);
            } else {
                leftPanning = 1;
                rightPanning = 1;
            }
        } else {
            leftPanning = (float) cos((panning + 1) * PI / 4);
            rightPanning = (float) sin((panning + 1) * PI / 4);
        }

        // now write transformed sample to the ring buffer
        int frame = 0;
        int frameCapacity = currentFrameCapacity;
        int bufferIndex = currentBuffer;
        int bufferOffset = frameOffset * frameSize / sampleBytes;

        while (frame < sampleFrameCount) {
            int framesToWrite = min(frameCapacity, sampleFrameCount - frame);

            float[] output = internalBuffers[bufferIndex];

            // calculate endFrame index (exclusive)
            int endFrame = min(sampleFrameCount, frame + framesToWrite);

            // write the transformed sample section into the target buffer
            for (; frame < endFrame; frame++) {  // TODO optimize
                float exactIdx = frame * pitch;
                int idx = (int) exactIdx;
                float delta = exactIdx - idx;

                if (idx + 1 >= baseFrameCount) {
                    frame = endFrame;
                    break;
                }

                for (int channel = 0; channel < channels; channel++) {
                    // lerp samples
                    int leftIdx = (idx * channels + channel) * sampleBytes;
                    int rightIdx = ((idx + 1) * channels + channel) * sampleBytes;

                    final float threshold = abs((float) Short.MIN_VALUE);
                    float leftSample = sample.getShort(leftIdx) / threshold;
                    float rightSample = sample.getShort(rightIdx) / threshold;

                    float interpolatedSample = (1.f - delta) * leftSample + delta * rightSample;

                    // apply volume
                    interpolatedSample *= volume;

                    // apply panning
                    interpolatedSample *= (channel == 0 ? leftPanning : rightPanning);

                    // mix with other sample
                    output[bufferOffset] += interpolatedSample;
                    bufferOffset++;
                }
            }

            bufferIndex = (bufferIndex + 1) % internalBuffers.length;
            frameCapacity = bufferFrameCapacity;
            bufferOffset = 0;
        }

        return true;
    }

    private float getPitch(Note note) {
        final byte instrument = note.instrument();
        CustomInstrument custom = song.instruments().custom(instrument);

        byte key;

        if (custom != null) {
            key = (byte) (note.key() + custom.key() - 45);
        } else {
            key = note.key();
        }

        return NoteHelper.openAlPitch((short) (key * 100 + note.pitch()));  // (0.0, any]
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

    public ByteBuffer processBuffer(int idx) {
        float[] samples = internalBuffers[idx];
        ByteBuffer output = buffers[idx];

        // floating point samples might be outside the playable 16-bit range
        // apply dynamic-range-compression in order to make everything playable
        // this reduces audio over-amplification and clipping significantly
        compressor.process(samples, output);

        return output;
    }
    
    public enum StereoMode {
        EQUAL_POWER,
        SPATIAL
    }
}
