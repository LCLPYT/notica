package work.lclpnet.notica.impl.mix;

import javax.sound.sampled.AudioFormat;

/// A feed-forward audio compressor inspired by the [openal-soft compressor](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp)
/// and [Daniel Rudrich's compressor](https://github.com/DanielRudrich/SimpleCompressor/blob/master/src/SimpleCompressor.h).
public record Compressor(GainReduction gainReduction, AudioFormat format) {

    public void process(final int frameCount, float[] samples, float[] next, float[] output) {
        final int lookaheadSamples = gainReduction.getLookaheadSamples();

        float[] sideChain = new float[frameCount + lookaheadSamples];

        // linear gain reduction factor will be put into sideChain
        gainReduction.lookAheadGainReduction(samples, next, sideChain);

        if (output.length < frameCount * 2 || samples.length < frameCount * 2) return;

        // left channel
        for (int i = 0; i < frameCount; i++) {
            output[i] = samples[i] * sideChain[i];
        }

        // right channel
        for (int i = 0; i < frameCount; i++) {
            output[i + frameCount] = samples[i + frameCount] * sideChain[i];
        }
    }

    public void reset() {
        gainReduction.reset();
    }
}
