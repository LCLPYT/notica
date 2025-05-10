package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.Nullable;

import static java.lang.Math.*;
import static net.minecraft.util.math.MathHelper.lerp;

/// Heavily inspired by the [openal-soft compressor](https://github.com/kcat/openal-soft/blob/master/core/mastering.cpp).
public class GainReduction {

    private final float thresholdLn;
    private final float slope;
    private final float crestCoeff;
    private final float gainEstimateLn;
    private final float adaptCoeff;
    private final float attackSamples;
    private final float releaseSamples;
    private final int lookaheadSamples;
    private final @Nullable Condensator condensator;

    private float lastPeak = 0f;
    private float lastRms = 0f;
    private float lastAttack = 0f;
    private float lastRelease = 0f;
    private float lastGainDev = 0f;

    public GainReduction(int sampleRate, float lookaheadSec, float thresholdDb, float attackSec, float releaseSec,
                         float holdSec, float ratio, float crestReleaseSec, float adaptationSec) {

        this.thresholdLn = (float) log(10f) * 0.05f * thresholdDb;
        this.attackSamples = max(1.f, attackSec * sampleRate);
        this.releaseSamples = max(1.f, releaseSec * sampleRate);
        this.lookaheadSamples = max(0, round(lookaheadSec * sampleRate));
        this.slope = 1.f / ratio - 1.f;

        this.crestCoeff = (float) exp(-1.f / (crestReleaseSec * (float) sampleRate));
        this.adaptCoeff = (float) exp(-1.f / (adaptationSec * (float) sampleRate));
        this.gainEstimateLn = thresholdLn * -0.5f * slope;

        int holdSamples = max(0, round(holdSec * sampleRate));

        if (lookaheadSamples > 0 && holdSamples > 1) {
            condensator = new Condensator(holdSamples);
        } else {
            condensator = null;
        }
    }

    public void lookAheadGainReduction(float[] interleavedSamples, float[] sideChain) {
        computeLinearSideChain(interleavedSamples, sideChain);

        float[] crest = new float[sideChain.length];

        computeCrestFactor(sideChain, crest);

        if (condensator != null) {
            toLogarithmicCondensate(sideChain, condensator);
        } else {
            toLogarithmic(sideChain);
        }

        computeGainReduction(sideChain, crest);
    }

    private void computeLinearSideChain(float[] interleavedSamples, float[] sideChain) {
        for (int i = 0; i < sideChain.length; i++) {
            // sideChain sample is the max abs gain across all channels
            float left = abs(interleavedSamples[2 * i]);
            float right = abs(interleavedSamples[2 * i + 1]);

            sideChain[i] = max(left, right);
        }
    }

    private void computeCrestFactor(float[] sideChain, float[] crest) {
        // calculate crest factor for each side-chain sample in linear domain
        float peak = this.lastPeak;
        float rms = this.lastRms;

        for (int i = 0; i < sideChain.length; i++) {
            float sampleSq = clamp(sideChain[i] * sideChain[i], 1e-6f, 1e+6f);

            peak = max(peak, lerp(crestCoeff, sampleSq, peak));
            rms = lerp(crestCoeff, sampleSq, rms);

            crest[i] = peak / rms;
        }

        this.lastPeak = peak;
        this.lastRms = rms;
    }

    private void toLogarithmic(float[] sideChain) {
        for (int i = 0; i < sideChain.length; i++) {
            float sample = sideChain[i];

            sideChain[i] = (float) log(max(1e-6f, sample));
        }
    }

    private void toLogarithmicCondensate(float[] sideChain, Condensator condensator) {
        for (int i = 0; i < sideChain.length; i++) {
            float sample = sideChain[i];
            float logSample = (float) log(max(1e-6f, sample));

            sideChain[i] = condensator.feed(logSample);
        }
    }

    private void computeGainReduction(float[] sideChain, float[] crest) {
        float releaseEnvelope = lastRelease;
        float attackEnvelope = lastAttack;
        float smoothedGainDeviation = lastGainDev;

        for (int i = 0; i < sideChain.length; i++) {
            // adjust knee width
            float knee = max(0f, 2.5f * (smoothedGainDeviation * gainEstimateLn));
            float kneeHalf = 0.5f * knee;

            // calculate how much the gain should be reduced
            int lookAheadIndex = min(i + lookaheadSamples, sideChain.length - 1); // TODO smooth with next sample
            float overShoot = sideChain[lookAheadIndex] - thresholdLn;  // TODO optimize lookup
            float gainReduction = calcGainReduction(overShoot, kneeHalf);  // y_G = gain reduction

            // adjust attack and release
            float crestVal = crest[i];
            float attackSamples = 2.f * this.attackSamples / crestVal;
            float attackCoeff = (float) exp(-1.f / attackSamples);
            float releaseSamples = 2.f * this.releaseSamples / crestVal - attackSamples;
            float releaseCoeff = (float) exp(-1.f / releaseSamples);

            // adjust reduction depending on current attack/hold/release phase
            final float attenuation = -slope * gainReduction;
            releaseEnvelope = max(attenuation, lerp(releaseCoeff, attenuation, releaseEnvelope));
            attackEnvelope = lerp(attackCoeff, releaseEnvelope, attackEnvelope);

            smoothedGainDeviation = lerp(adaptCoeff, -1f * (attackEnvelope + gainEstimateLn), smoothedGainDeviation);

            // de-clip
            smoothedGainDeviation = max(smoothedGainDeviation, sideChain[i] - attackEnvelope - thresholdLn - gainEstimateLn);

            // post gain
            float postGain = -(smoothedGainDeviation + gainEstimateLn);

            sideChain[i] = (float) exp(postGain - attackEnvelope);
        }

        // preserve state for next sample
        lastRelease = releaseEnvelope;
        lastAttack = attackEnvelope;
        lastGainDev = smoothedGainDeviation;
    }

    private float calcGainReduction(float overShoot, float kneeHalf) {
        if (overShoot <= -kneeHalf) {
            return 0f;
        }

        if (overShoot > -kneeHalf && overShoot < kneeHalf) {
            return (overShoot + kneeHalf) * (overShoot + kneeHalf) / (kneeHalf * 4f);
        }

        return overShoot;
    }

    public void reset() {
        lastPeak = 0;
        lastRms = 0;
        lastAttack = 0;
        lastRelease = 0;
        lastGainDev = 0;
    }

    private static class Condensator {

        private final int holdSamples;
        private final float[] peaks;
        private final int[] expireTimes;
        private int time;
        private int hi;

        private Condensator(int holdSamples) {
            this.holdSamples = holdSamples;
            this.peaks = new float[holdSamples];
            this.expireTimes = new int[holdSamples];

            hi = 0;
            time = 0;

            peaks[hi] = Float.NEGATIVE_INFINITY;
            expireTimes[hi] = holdSamples;
        }

        public float feed(float sample) {
            int t = time++;

            // check if highest peak expired
            if (t >= expireTimes[hi]) {
                hi = (hi + 1) % holdSamples;
            }

            if (sample >= peaks[hi]) {
                peaks[hi] = sample;
                expireTimes[hi] = t + holdSamples;
                return sample;
            }

            // insert sample at corresponding index
            // can't use binarySearch as arrays are ring buffers
            for (int i = 1; i < holdSamples; i++) {
                int j = (hi + i) % holdSamples;

                if (sample >= peaks[j] || t >= expireTimes[j]) {
                    peaks[j] = sample;
                    expireTimes[j] = t + holdSamples;
                    break;
                }
            }

            return peaks[hi];
        }
    }
}
