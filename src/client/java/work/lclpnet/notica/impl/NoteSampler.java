package work.lclpnet.notica.impl;

import work.lclpnet.notica.api.data.Note;

public interface NoteSampler {

    int sample(Note note, float volume, short layerPanning, float[] sampleBuffer);
}
