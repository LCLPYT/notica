package work.lclpnet.notica.impl;

import java.nio.ByteBuffer;

import static java.lang.Math.*;
import static net.minecraft.util.math.MathHelper.clamp;

/// A feed-forward audio compressor.
/// Inspired by [openal-soft](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp).
public class Compressor {

    private final Settings settings;
    private float envelope = 0f;
    private float holdCounter = 0f;

    public Compressor(Settings settings) {
        this.settings = settings;
    }

    public void process(float[] samples, ByteBuffer output) {
        float preGain = (float) pow(10, settings.preGainDb / 20.0);

        for (int i = 0; i < samples.length; i++) {
            samples[i] *= preGain;
        }

        // TODO inline
        float[] gainReduction = new float[samples.length];

        int lookaheadSamples = round(settings.lookaheadTimeMs / 1000.0f * settings.sampleRate);
        int holdSamples = round(settings.holdTimeMs / 1000.0f * settings.sampleRate);
        float attackCoeff = (float) Math.exp(-1.0 / (settings.attackTimeMs / 1000.0 * settings.sampleRate));
        float releaseCoeff = (float) Math.exp(-1.0 / (settings.releaseTimeMs / 1000.0 * settings.sampleRate));

        // Peak envelope detection with lookahead and knee handling
        for (int i = 0; i < samples.length; i++) {
            float targetGain = getTargetGain(samples, i, lookaheadSamples);

            if (targetGain < envelope) {
                envelope = attackCoeff * (envelope - targetGain) + targetGain;
                holdCounter = holdSamples;
            } else if (holdCounter > 0) {
                holdCounter--;
            } else {
                envelope = releaseCoeff * (envelope - targetGain) + targetGain;
            }

            gainReduction[i] = envelope;
        }

        // Apply gain reduction and post gain
        float postGain = (float) pow(10, settings.postGainDb / 20.0);

        output.position(0);

        for (int i = 0; i < samples.length; i++) {
            float processed = samples[i] * gainReduction[i] * postGain;

            // Limit and dither
            processed = clamp(processed, -1f, 1f);

            float dither = (float)((random() - 0.5) / (Short.MAX_VALUE + 1f));
            int quantized = round((processed + dither) * Short.MAX_VALUE);

            quantized = clamp(quantized, Short.MIN_VALUE, Short.MAX_VALUE);

            output.putShort((short) quantized);
        }

        output.flip();
    }

    private float getTargetGain(float[] samples, int i, int lookaheadSamples) {
        int lookaheadIndex = Math.min(i + lookaheadSamples, samples.length - 1);
        float peak = Math.abs(samples[lookaheadIndex]);

        // dB level
        float inputDb = 20.0f * (float) Math.log10(peak + 1e-6f);
        float gainDb = getGainDb(inputDb);

        // Smooth gain changes using envelope detector
        return (float) pow(10, -gainDb / 20.0);
    }

    private float getGainDb(float inputDb) {
        float threshold = settings.thresholdDb;
        float knee = settings.kneeDb;
        float overDb = inputDb - threshold;

        float gainDb = 0.0f;

        if (overDb > 0.0f) {
            if (knee > 0.0f && overDb < knee) {
                // Soft knee
                float kneeRatio = overDb / knee;
                gainDb = (1 - 1 / settings.ratio) * kneeRatio * knee / 2;
            } else {
                // Hard knee
                gainDb = (1 - 1 / settings.ratio) * overDb;
            }
        }

        return gainDb;
    }

    public static class Settings {
        public final int sampleRate;
        public float lookaheadTimeMs = 1.0f;
        public float holdTimeMs = 2.0f;
        public float attackTimeMs = 10.0f;
        public float releaseTimeMs = 100.0f;
        public float preGainDb = 0.0f;
        public float postGainDb = 0.0f;
        public float thresholdDb = -1.0f;
        public float ratio = Float.POSITIVE_INFINITY;
        public float kneeDb = 0.0f;
        public float slope = 1.0f;

        public Settings(int sampleRate) {
            this.sampleRate = sampleRate;
        }
    }
}
