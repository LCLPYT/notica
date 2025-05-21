package work.lclpnet.notica.benchmark.impl;

import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.impl.NoteSampler;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.util.NoteHelper;

import javax.sound.sampled.AudioFormat;

import static java.lang.Math.*;

@SuppressWarnings("DuplicatedCode")
public class CatmullRomBaselineNoteSampler implements NoteSampler {

    private static final float INV_SHORT = 1f / Short.MAX_VALUE;

    private final SoundSampleManager sampleManager;
    private final AudioFormat format;
    private final SoundMixer.StereoMode stereoMode;
    private final Instruments instruments;

    public CatmullRomBaselineNoteSampler(SoundSampleManager sampleManager, AudioFormat format, SoundMixer.StereoMode stereoMode, Instruments instruments) {
        this.sampleManager = sampleManager;
        this.format = format;
        this.stereoMode = stereoMode;
        this.instruments = instruments;
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
        final int inFrames = in.length / channels;
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

        // transform left
        transform(in, 0, inFrames, out, 0, outFrames, pitch);
        mul(out, 0, outFrames, volume * leftPanning);

        // transform right
        transform(in, inFrames, inFrames, out, outFrames, outFrames, pitch);
        mul(out, outFrames, outFrames, volume * rightPanning);

        return outFrames;
    }

    private static void transform(final float[] in, final int inOffset, final int inLen,
                           final float[] out, final int outOffset, final int outLen,
                           final float pitch) {

        for (int i = 0; i < outLen; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = max(0, min(inOffset + j - 1, inLen - 1));
            int i1 = max(0, min(inOffset + j, inLen - 1));
            int i2 = max(0, min(inOffset + j + 1, inLen - 1));
            int i3 = max(0, min(inOffset + j + 2, inLen - 1));

            float p0 = in[i0];
            float p1 = in[i1];
            float p2 = in[i2];
            float p3 = in[i3];

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;
            p2 *= INV_SHORT;
            p3 *= INV_SHORT;

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float sample = 0.5f * (
                    (2 * p1) +
                    (-p0 + p2) * t +
                    (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                    (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            );

            out[outOffset + i] = sample;
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
