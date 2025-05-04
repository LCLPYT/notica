package work.lclpnet.notica.impl.mix;

import java.nio.ByteBuffer;

import static java.lang.Math.round;
import static net.minecraft.util.math.MathHelper.clamp;

/// A feed-forward audio compressor.
/// Inspired by [openal-soft](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp).
public class Compressor {

    private final GainReduction gainReduction;

    public Compressor(GainReduction gainReduction) {
        this.gainReduction = gainReduction;
    }

    public void process(float[] samples, ByteBuffer output) {
        float[] reduction = new float[samples.length];

        gainReduction.lookAheadGainReduction(samples, reduction);

        for (int i = 0; i < samples.length; i++) {
            samples[i] *= reduction[i];
        }

        output.position(0);

        for (float sample : samples) {
            int quantized = clamp(round(sample * Short.MAX_VALUE), Short.MIN_VALUE, Short.MAX_VALUE);

            output.putShort((short) quantized);
        }

        output.flip();
    }
}
