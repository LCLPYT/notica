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

public class BaselineNoteSampler implements NoteSampler {

    private final SoundSampleManager sampleManager;
    private final AudioFormat format;
    private final StereoMode stereoMode;
    private final Instruments instruments;

    public BaselineNoteSampler(SoundSampleManager sampleManager, AudioFormat format, StereoMode stereoMode, Instruments instruments) {
        this.sampleManager = sampleManager;
        this.format = format;
        this.stereoMode = stereoMode;
        this.instruments = instruments;
    }

    @Override
    public int sample(Note note, float volume, short layerPanning, float[] sampleBuffer) {
        float[] sample = sampleManager.getSample(note.instrument());

        if (sample.length == 0) {
            return -1;
        }

        volume *= note.velocity() * 1e-2f;

        if (volume <= 0f) {
            return -1;
        }

        float pitch = getPitch(note);
        float panning = NoteHelper.normalizePanning(layerPanning, note.panning());  // [-1, 1], 0=center

        final int channels = format.getChannels();
        final int baseFrameCount = sample.length / channels;
        final int sampleFrameCount = (int) (baseFrameCount / pitch);

        // check if sample can fit into the buffer
        final int capacity = sampleBuffer.length / channels;

        if (sampleFrameCount > capacity) {
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

        // transform left
        for (int frame = 0; frame < sampleFrameCount; frame++) {
            float exactIdx = frame * pitch;
            int idx = (int) exactIdx;
            float delta = exactIdx - idx;

            if (idx + 1 >= baseFrameCount) {
                break;
            }

            float leftSample = sample[idx];
            float rightSample = sample[idx + 1];
            float interpolatedSample = (1.f - delta) * leftSample + delta * rightSample;

            // apply volume
            interpolatedSample *= volume;

            // apply panning
            interpolatedSample *= leftPanning;

            sampleBuffer[frame] = interpolatedSample;
        }

        // transform right
        for (int frame = 0; frame < sampleFrameCount; frame++) {
            float exactIdx = frame * pitch;
            int idx = (int) exactIdx;
            float delta = exactIdx - idx;

            if (idx + 1 >= baseFrameCount) {
                break;
            }

            int realIdx = idx + baseFrameCount;
            float leftSample = sample[realIdx];
            float rightSample = sample[realIdx + 1];
            float interpolatedSample = (1.f - delta) * leftSample + delta * rightSample;

            // apply volume
            interpolatedSample *= volume;

            // apply panning
            interpolatedSample *= rightPanning;

            sampleBuffer[frame + sampleFrameCount] = interpolatedSample;
        }

        return sampleFrameCount;
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
