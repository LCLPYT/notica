package work.lclpnet.notica.impl.mix;

import java.nio.ByteBuffer;

import static java.lang.Math.round;
import static net.minecraft.util.math.MathHelper.clamp;

/// A feed-forward audio compressor inspired by the [openal-soft compressor](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp)
/// and [Daniel Rudrich's compressor](https://github.com/DanielRudrich/SimpleCompressor/blob/master/src/SimpleCompressor.h).
public class Compressor {

    private final GainReduction gainReduction;

    public Compressor(GainReduction gainReduction) {
        this.gainReduction = gainReduction;
    }

    public void process(float[] samples, ByteBuffer output) {
        // assume stereo
        final int channels = 2;
        float[] sideChain = new float[samples.length / channels];

        // linear gain reduction factor will be put into sideChain
        gainReduction.lookAheadGainReduction(samples, sideChain);

        for (int i = 0; i < sideChain.length; i++) {
            samples[i] *= sideChain[i];
        }

        for (int i = 0; i < sideChain.length; i++) {
            samples[i + sideChain.length] *= sideChain[i];
        }

        // re-interleave
        output.position(0);

        for (int i = 0; i < sideChain.length; i++) {
            float sl = samples[i];
            float sr = samples[i + sideChain.length];
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
