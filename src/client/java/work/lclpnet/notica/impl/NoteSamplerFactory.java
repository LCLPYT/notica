package work.lclpnet.notica.impl;

import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.Instruments;

import javax.sound.sampled.AudioFormat;

public interface NoteSamplerFactory {
    NoteSampler create(SoundSampleManager sampleManager, AudioFormat format, StereoMode stereoMode, Instruments instruments);
}
