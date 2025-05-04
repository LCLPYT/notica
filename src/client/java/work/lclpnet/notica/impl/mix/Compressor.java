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

    public void process(float[] interleavedSamples, ByteBuffer output) {
        // assume stereo
        int channels = 2;
        float[] reduction = new float[interleavedSamples.length / channels];

        gainReduction.lookAheadGainReduction(interleavedSamples, reduction);

        for (int i = 0; i < interleavedSamples.length; i++) {
            interleavedSamples[i] *= reduction[i / channels];
        }

        output.position(0);

        for (float sample : interleavedSamples) {
            int quantized = clamp(round(sample * Short.MAX_VALUE), Short.MIN_VALUE, Short.MAX_VALUE);

            output.putShort((short) quantized);
        }

        output.flip();
    }
}
