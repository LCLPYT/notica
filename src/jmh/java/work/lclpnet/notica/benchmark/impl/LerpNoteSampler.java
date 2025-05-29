package work.lclpnet.notica.benchmark.impl;

import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.impl.NoteSampler;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.NoteHelper;

import javax.sound.sampled.AudioFormat;

import static java.lang.Math.*;

@SuppressWarnings("DuplicatedCode")
public class LerpNoteSampler implements NoteSampler {

    private final SoundSampleManager sampleManager;
    private final AudioFormat format;
    private final StereoMode stereoMode;
    private final Instruments instruments;

    public LerpNoteSampler(SoundSampleManager sampleManager, AudioFormat format, StereoMode stereoMode, Instruments instruments) {
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
        final int inFrames = (in.length - 8) / channels;
        final int outFrames = (int) (inFrames / pitch);

        // check if sample can fit into the buffer
        final int capacity = out.length / channels;

        if (outFrames > capacity) {
            return -1;
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

        float[] xs = new float[outFrames];

        for (int i = 0; i < xs.length; i++) {
            xs[i] = i * pitch;
        }

        // transform left
        transform(outFrames, xs, in, out, 2, 0);
        mul(out, 0, outFrames, volume * leftPanning);

        // transform right
        transform(outFrames, xs, in, out, inFrames + 6, outFrames);
        mul(out, outFrames, outFrames, volume * rightPanning);

        return outFrames;
    }

    private static void transform(final int len, final float[] xs, final float[] in, final float[] out,
                                  final int inOffset, final int outOffset) {

        for (int i = 0; i < len; i++) {
            float x = xs[i];
            int j = (int) x;
            float t = x - j;

            float leftSample = in[inOffset + j];
            float rightSample = in[inOffset + j + 1];
            float interpolatedSample = (1.f - t) * leftSample + t * rightSample;

            out[outOffset + i] = interpolatedSample;
        }
    }

    private static void mul(final float[] vals, final int offset, final int len, final float volume) {
        for (int i = 0; i < len; i++) {
            vals[i + offset] *= volume;
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
