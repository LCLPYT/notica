package work.lclpnet.notica.impl.mix;

import java.nio.ByteBuffer;

import static java.lang.Math.clamp;
import static java.lang.Math.round;

/// A feed-forward audio compressor inspired by the [openal-soft compressor](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp)
/// and [Daniel Rudrich's compressor](https://github.com/DanielRudrich/SimpleCompressor/blob/master/src/SimpleCompressor.h).
public record Compressor(GainReduction gainReduction) {

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

        for (int i = 0; i < frameCount; i++) {
            float sl = samples[i];
            float sr = samples[i + frameCount];
            int ql = clamp(round(sl * Short.MAX_VALUE), Short.MIN_VALUE, Short.MAX_VALUE);
            int qr = clamp(round(sr * Short.MAX_VALUE), Short.MIN_VALUE, Short.MAX_VALUE);

            output.putShort((short) ql);
            output.putShort((short) qr);
        }

        output.flip();
    }

    public void reset() {
        gainReduction.reset();
    }
}
