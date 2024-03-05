package work.lclpnet.kibu.nbs.util;

/**
 * Logic from <a href="https://github.com/koca2000/NoteBlockAPI/blob/afb7752c48013df6ded6d02c1c20d4aa5962a653/src/main/java/com/xxmicloxx/NoteBlockAPI/utils/NoteUtils.java">NoteBlockAPI</a>.
 */
public class NoteHelper {

    private NoteHelper() {}

    private static final float[] PITCH_LUT;

    static {
        final int base = 2400;
        final double half = base * 0.5;
        final int len = base + 1;

        PITCH_LUT = new float[len];

        for (int i = 0; i < len; i++) {
            PITCH_LUT[i] = (float) Math.pow(2, (i - half) / half);
        }
    }

    public static float getVanillaPitch(byte key, short pitch) {
        key += (byte) (pitch / 100);
        pitch %= 100;

        // -15 base_-2
        // 9 base_-1
        // 33 base
        // 57 base_1
        // 81 base_2
        // 105 base_3
        if (key < 9) {
            key -= -15;
        } else if (key < 33) {
            key -= 9;
        } else if (key < 57) {
            key -= 33;
        } else if (key < 81) {
            key -= 57;
        } else if (key < 105) {
            key -= 81;
        }

        return PITCH_LUT[key * 100 + pitch];
    }
}
