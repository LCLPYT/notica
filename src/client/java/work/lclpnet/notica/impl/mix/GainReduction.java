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

    private float state = 0.f;
    private int holdCounter = 0;

    public GainReduction(int sampleRate, float lookaheadSec, float thresholdDb, float kneeDb,
                         float attackSec, float releaseSec, float holdSec, float ratio) {
        this.sampleRate = sampleRate;
        this.lookaheadSec = lookaheadSec;
        this.thresholdDb = thresholdDb;
        this.kneeDb = kneeDb;

        this.kneeHalfDb = kneeDb * 0.5f;
        this.alphaAttack = 1.f - gainSeconds(attackSec);
        this.alphaRelease = 1.f - gainSeconds(releaseSec);
        this.holdSamples = round(holdSec * sampleRate);
        this.slope = 1.f / ratio - 1.f;
    }

    public void lookAheadGainReduction(float[] interleavedSamples, float[] reduction) {
        int lookaheadSamples = round(lookaheadSec * sampleRate);

        for (int i = 0; i < reduction.length; i++) {
            // find max gain at future sample
            int lookaheadIndex = min(i + lookaheadSamples, reduction.length - 1);

            // find max lookahead sample from all channels
            float sideChainSample = max(abs(interleavedSamples[2 * lookaheadIndex]), abs(interleavedSamples[2 * lookaheadIndex + 1]));
            float levelDb = 20.f * (float) log10(sideChainSample);

            // calculate how much the gain should be reduced
            float overShoot = levelDb - thresholdDb;
            float gainReduction = calcGainReduction(overShoot);

            // adjust reduction depending on current attack/hold/release phase
            float diff = gainReduction - state;

            if (diff < 0.f) {
                state += alphaAttack * diff;
                holdCounter = holdSamples;
            } else if (holdCounter > 0) {
                holdCounter--;
            } else {
                state += alphaRelease * diff;
            }

            // convert to scalar with which the sample can be scaled (log-domain to sample-domain)
            float factor = (float) pow(10.d, state * 0.05d);
            reduction[i] = factor;
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

    private float gainSeconds(float seconds) {
        return (float) exp(-1.f / (seconds * (float) sampleRate));
    }
}
