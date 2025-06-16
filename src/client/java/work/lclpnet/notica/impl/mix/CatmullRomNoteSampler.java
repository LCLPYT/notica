package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.util.NoteHelper;

import javax.sound.sampled.AudioFormat;

import static java.lang.Math.*;

@SuppressWarnings("DuplicatedCode")
public class CatmullRomNoteSampler implements NoteSampler {

    private final SoundSampleManager sampleManager;
    private final AudioFormat format;
    private final StereoMode stereoMode;
    private final Instruments instruments;

    public CatmullRomNoteSampler(SoundSampleManager sampleManager, AudioFormat format, StereoMode stereoMode, Instruments instruments) {
        this.sampleManager = sampleManager;
        this.format = format;
        this.stereoMode = stereoMode;
        this.instruments = instruments;
    }

    public static float[] paddedSample(float[] sample) {
        if (sample.length % 2 == 1) {
            throw new IllegalArgumentException("Non stereo sample");
        }

        float[] padded = new float[sample.length + 8];

        final int frames = sample.length / 2;

        // left channel
        System.arraycopy(sample, 0, padded, 2, frames);

        padded[0] = padded[1] = padded[2];
        padded[frames + 1] = padded[frames] = padded[frames - 1];

        // right channel
        System.arraycopy(sample, frames, padded, frames + 4, frames);

        padded[frames + 2] = padded[frames + 3] = padded[frames + 4];
        padded[sample.length + 7] = padded[sample.length - 6] = padded[sample.length - 5];

        return padded;
    }

    public static float[] changePitch(float[] sample, float pitch, AudioFormat targetFormat) {
        if (pitch == 1.f) {
            return sample;
        }

        int baseSamples = sample.length;
        int transformedSamples = (int) (baseSamples / pitch);

        float[] paddedSrc = paddedSample(sample);
        float[] transformed = new float[transformedSamples];

        resample(paddedSrc, transformed, pitch, targetFormat);

        return transformed;
    }

    @Override
    public int sample(Note note, float volume, short layerPanning, float[] out) {
        float[] in = sampleManager.getSample(note.instrument());

        if (in.length == 0) {
            return -1;
        }

        volume *= note.velocity() * 1e-2f;

        if (volume <= 0f) {
            return -1;
        }

        float pitch = getPitch(note);
        int outFrames = resample(in, out, pitch, format);

        float panning = NoteHelper.normalizePanning(layerPanning, note.panning());  // [-1, 1], 0=center
        applyVolumePanning(out, outFrames, volume, panning, stereoMode);

        return outFrames;
    }

    public static int resample(float[] in, float[] out, float pitch, AudioFormat format) {
        final int channels = format.getChannels();
        final int inFrames = (in.length - 8) / channels;  // don't count padding
        final int outFrames = (int) (inFrames / pitch);

        // check if sample can fit into the buffer
        final int capacity = out.length / channels;

        if (outFrames > capacity) {
            return -1;
        }

        float[] xs = new float[outFrames];

        for (int i = 0; i < xs.length; i++) {
            xs[i] = i * pitch;
        }

        // transform left
        resample(outFrames, xs, in, out, 0, 0);

        // transform right
        resample(outFrames, xs, in, out, inFrames + 4, outFrames);

        return outFrames;
    }

    public static void applyVolumePanning(float[] out, int outFrames, float volume, float panning, StereoMode stereoMode) {
        if (volume == 1.f && panning == 0.f) {
            return;
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
                leftPanning = (float) cos(PI / 4);
                rightPanning = (float) sin(PI / 4);
            }
        } else {
            leftPanning = (float) cos((panning + 1) * PI / 4);
            rightPanning = (float) sin((panning + 1) * PI / 4);
        }

        // transform left
        mul(out, 0, outFrames, volume * leftPanning);

        // transform right
        mul(out, outFrames, outFrames, volume * rightPanning);
    }

    private static void resample(final int len, final float[] xs, final float[] y_in, final float[] y_out, final int in_offset, final int out_offset) {
        // TODO hosting
        for (int i = 0; i < len; i++) {
            final float x = xs[i];

            final int j = (int) x;
            final float t = x - (float) j;

            float p0 = y_in[in_offset + j + 1];
            float p1 = y_in[in_offset + j + 2];
            float p2 = y_in[in_offset + j + 3];
            float p3 = y_in[in_offset + j + 4];

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float a = Math.fma(-p0 + p2, t, 2f * p1);
            float b = Math.fma(2f * p0 - 5f * p1 + 4f * p2 - p3, t2, a);
            float c = Math.fma(-p0 + 3f * p1 - 3f * p2 + p3, t3, b);

            y_out[out_offset + i] = 0.5f * c;
        }
    }

    private static void mul(final float[] vals, final int offset, final int len, final float volume) {
        for (int i = 0; i < len; i++) {
            vals[offset + i] *= volume;
        }
    }

    private float getPitch(Note note) {
        final byte instrument = note.instrument();
        CustomInstrument custom = instruments.custom(instrument);

        byte key;

        if (custom != null) {
            key = (byte) (note.key() + custom.key() - 45);
        } else {
            key = note.key();
        }

        return NoteHelper.openAlPitch((short) (key * 100 + note.pitch()));  // (0.0, any]
    }
}
