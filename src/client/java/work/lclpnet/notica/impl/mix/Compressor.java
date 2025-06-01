package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.impl.UnifiedSoundLoader;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/// A feed-forward audio compressor inspired by the [openal-soft compressor](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp)
/// and [Daniel Rudrich's compressor](https://github.com/DanielRudrich/SimpleCompressor/blob/master/src/SimpleCompressor.h).
public record Compressor(GainReduction gainReduction, AudioFormat format) {

    public void process(final int frameCount, float[] samples, float[] next, ByteBuffer output) {
        final int lookaheadSamples = gainReduction.getLookaheadSamples();

        float[] sideChain = new float[frameCount + lookaheadSamples];

        // linear gain reduction factor will be put into sideChain
        gainReduction.lookAheadGainReduction(samples, next, sideChain);

        for (int i = 0; i < frameCount; i++) {
            samples[i] *= sideChain[i];
        }

        for (int i = 0; i < frameCount; i++) {
            samples[i + frameCount] *= sideChain[i];
        }

        // re-interleave
        output.position(0);
        output.limit(output.capacity());

        UnifiedSoundLoader.toInterleavedBytes(samples, frameCount, output, format);

        output.flip();
    }

    public void reset() {
        gainReduction.reset();
    }
}
