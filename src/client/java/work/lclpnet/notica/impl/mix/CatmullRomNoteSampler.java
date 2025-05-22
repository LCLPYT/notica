package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.impl.NoteSampler;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.NoteHelper;

import javax.sound.sampled.AudioFormat;

import static java.lang.Math.*;

@SuppressWarnings("DuplicatedCode")
public class CatmullRomNoteSampler implements NoteSampler {

    private final SoundSampleManager sampleManager;
    private final AudioFormat format;
    private final SoundMixer.StereoMode stereoMode;
    private final Instruments instruments;

    public CatmullRomNoteSampler(SoundSampleManager sampleManager, AudioFormat format, SoundMixer.StereoMode stereoMode, Instruments instruments) {
        this.sampleManager = sampleManager;
        this.format = format;
        this.stereoMode = stereoMode;
        this.instruments = instruments;
    }

    public static float[] paddedSample(float[] sample) {
        float[] padded = new float[sample.length + 4];

        System.arraycopy(sample, 0, padded, 2, sample.length);

        padded[0] = padded[1] = padded[2];
        padded[sample.length + 1] = padded[sample.length] = padded[sample.length - 1];

        return padded;
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
        float panning = NoteHelper.normalizePanning(layerPanning, note.panning());  // [-1, 1], 0=center

        final int channels = format.getChannels();
        final int inFrames = (in.length - 4) / channels;  // don't count padding
        final int outFrames = (int) (inFrames / pitch);

        // check if sample can fit into the buffer
        final int capacity = out.length / channels;

        if (outFrames > capacity) {
            return -1;
        }

        // calculate panning
        float leftPanning;
        float rightPanning;

        if (stereoMode == SoundMixer.StereoMode.SPATIAL) {
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

        float[] xs = new float[outFrames];

        for (int i = 0; i < xs.length; i++) {
            xs[i] = i * pitch;
        }

        // transform left
        evaluateSimd(outFrames, xs, in, out, 0, 0);
        mul(out, 0, outFrames, volume * leftPanning);

        // transform right
        evaluateSimd(outFrames, xs, in, out, inFrames, outFrames);
        mul(out, outFrames, outFrames, volume * rightPanning);

        return outFrames;
    }

    private static void evaluateSimd(final int len, final float[] xs, final float[] y_in, final float[] y_out, final int in_offset, final int out_offset) {
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
        for (int i = offset; i < len; i++) {
            vals[i] *= volume;
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
