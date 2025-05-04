package work.lclpnet.notica.impl.mix;

import static java.lang.Math.*;

public class GainReduction {

    private final int sampleRate;
    private final float lookaheadSec;
    private final float thresholdDb;
    private final float kneeDb;
    private final float slope;
    private final float kneeHalfDb;
    private final float alphaAttack;
    private final float alphaRelease;
    private final int holdSamples;

    private float maxInputLevel = Float.NEGATIVE_INFINITY;
    private float maxGainReduction = 0.f;
    private float state = 0.f;
    private int holdCounter = 0;

    public GainReduction(int sampleRate, float lookaheadSec, float thresholdDb, float kneeDb,
                         float attackSec, float releaseSec, float holdSec, float ratio) {
        this.sampleRate = sampleRate;
        this.lookaheadSec = lookaheadSec;
        this.thresholdDb = thresholdDb;
        this.kneeDb = kneeDb;

        this.kneeHalfDb = kneeDb * 0.5f;
        this.alphaAttack = 1.f - timeToGain(attackSec);
        this.alphaRelease = 1.f - timeToGain(releaseSec);
        this.holdSamples = round(holdSec * sampleRate);
        this.slope = 1.f / ratio - 1.f;
    }

    public void lookAheadGainReduction(float[] samples, float[] reduction) {
        int lookaheadSamples = round(lookaheadSec * sampleRate);

        maxInputLevel = Float.NEGATIVE_INFINITY;
        maxGainReduction = 0f;

        for (int i = 0; i < samples.length; i++) {
            int lookaheadIndex = min(i + lookaheadSamples, samples.length - 1);

            float lookaheadSample = samples[lookaheadIndex];
            float levelDb = 20.f * (float) log10(abs(lookaheadSample));

            if (levelDb > maxInputLevel) {
                maxInputLevel = levelDb;
            }

            float overShoot = levelDb - thresholdDb;
            float gainReduction = calcGainReduction(overShoot);

            float diff = gainReduction - state;

            if (diff < 0.f) {
                state += alphaAttack * diff;
                holdCounter = holdSamples;
            } else if (holdCounter > 0) {
                holdCounter--;
            } else {
                state += alphaRelease * diff;
            }

            float factor = (float) pow(10.d, state * 0.05d);
            reduction[i] = factor;

            if (state < maxGainReduction) {
                maxGainReduction = state;
            }
        }
    }

    private float calcGainReduction(float overShootDb) {
        if (overShootDb <= -kneeHalfDb) {
            return 0f;
        }

        if (overShootDb > -kneeHalfDb && overShootDb <= kneeHalfDb) {
            return 0.5f * slope * (overShootDb + kneeHalfDb) * (overShootDb + kneeHalfDb) / kneeDb;
        }

        return slope * overShootDb;
    }

    private float timeToGain(float seconds) {
        return (float) exp(-1.f / (seconds * (float) sampleRate));
    }

    public float getMaxGainReduction() {
        return maxGainReduction;
    }

    public float getMaxInputLevel() {
        return maxInputLevel;
    }

    public void reset() {
        state = 0f;
    }
}
