package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CatmullRomSplineTest {

    @Test
    void fmaExpressionMatches() {
        float t = 0.5f;
        float p0 = 1;
        float p1 = 2;
        float p2 = 3;
        float p3 = 4;

        float standard = catmullRom(t, p0, p1, p2, p3);
        float fma = catmullRomFMA(t, p0, p1, p2, p3);
        float moreFma = catmullRomMoreFMA(t, p0, p1, p2, p3);

        assertEquals(standard, fma, 1e-6f);
        assertEquals(standard, moreFma, 1e-6f);
    }

    private float catmullRom(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;

        return 0.5f * ((2 * p1)
                + (-p0 + p2) * t
                + ((2 * p0) + (-5 * p1) + (4 * p2) + (-p3)) * t2
                + ((3 * p1) + (-3 * p2) + (p3) + (-p0)) * t3
        );
    }

    private float catmullRomFMA(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;

        float a = Math.fma(-p0 + p2,    t,     2f*p1);
        float b = Math.fma(2f*p0 -5f*p1 +4f*p2 -p3, t2, a);
        float c = Math.fma(-p0 +3f*p1 -3f*p2 +p3,  t3, b);

        return 0.5f * c;
    }

    private float catmullRomMoreFMA(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;

        float s = Math.fma(-3, p2, p3 - p0);
        float v = Math.fma(3, p1, s);

        float m = Math.fma(4, p2, -p3);
        float u = Math.fma(-5, p1, m);
        float w = Math.fma(2, p0, u);

        float x = Math.fma(w, t2, v * t3);
        float y = Math.fma(-p0 + p2, t, x);
        float z = Math.fma(2, p1, y);

        return 0.5f * z;
    }

    @Test
    void fixedPointCorrect() {
        int n = 240_000;
        float pitch = 0.7f;

        int[] i1 = new int[n];
        int[] i2 = new int[n];

        float[] f1 = new float[n];
        float[] f2 = new float[n];

        floatingPointDirect(pitch, i1, f1);
        floatingPointAccumulate(pitch, i2, f2);

        assertArrayEquals(i1, i2, "float accumulate: Indices mismatch");
        assertArrayEquals(f1, f2, "float accumulate: Fractional mismatch");
    }

    private void floatingPointDirect(float pitch, int[] indices, float[] fractional) {
        for (int i = 0; i < indices.length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            indices[i] = j;
            fractional[i] = t;
        }
    }

    private void floatingPointAccumulate(double pitch, int[] indices, float[] fractional) {
        double x = 0f;

        for (int i = 0; i < indices.length; i++) {
            int j = (int) (float) x;
            float t = (float) x - j;

            indices[i] = j;
            fractional[i] = t;

            x += pitch;
        }
    }
}
