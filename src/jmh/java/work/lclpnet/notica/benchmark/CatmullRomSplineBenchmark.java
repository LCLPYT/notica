package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;

@SuppressWarnings({"FieldMayBeFinal", "DuplicatedCode"})
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Threads(4)
public class CatmullRomSplineBenchmark {

    private static final float INV_SHORT = 1f / Short.MAX_VALUE;

    @State(Scope.Thread)
    public static class ByteBufferState {

        int sampleCount;
        float pitch;
        ByteBuffer input;
        float[] output;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = ByteBuffer.allocate(2 * sampleCount);
            output = new float[(int) (sampleCount / pitch)];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);

                input.putShort((short) (sin * Short.MAX_VALUE));
            }

            input.flip();
        }
    }

    @State(Scope.Thread)
    public static class FloatArrayState {

        int sampleCount;
        float pitch;
        float[] input;
        float[] output;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = new float[sampleCount];
            output = new float[(int) (sampleCount / pitch)];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);

                input[i] = (float) sin;
            }
        }
    }

    @State(Scope.Thread)
    public static class PaddedByteBufferState {

        int sampleCount;
        float pitch;
        ByteBuffer input;
        float[] output;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = ByteBuffer.allocate(2 * (sampleCount + 4));  // 2 samples at the beginning, two at the end
            output = new float[(int) (sampleCount / pitch)];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            short quantized = (short) (Math.sin(offset) * Short.MAX_VALUE);
            input.putShort(quantized);
            input.putShort(quantized);

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);
                quantized = (short) (sin * Short.MAX_VALUE);

                input.putShort(quantized);
            }

            input.putShort(quantized);
            input.putShort(quantized);

            input.flip();
        }
    }

    @State(Scope.Thread)
    public static class PaddedFloatArrayState {

        int sampleCount;
        float pitch;
        float[] input;
        float[] output;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = new float[sampleCount + 4];
            output = new float[(int) (sampleCount / pitch)];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);

                input[i + 2] = (float) sin;
            }

            input[0] = input[1] = input[2];
            input[sampleCount] = input[sampleCount + 1] = input[sampleCount - 1];
        }
    }

    @State(Scope.Thread)
    public static class SimdPaddedFloatArrayState {

        int sampleCount;
        float pitch;
        float[] input;
        float[] output;
        float[] xs;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = new float[sampleCount + 4];
            output = new float[(int) (sampleCount / pitch)];
            xs = new float[output.length];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);

                input[i + 2] = (float) sin;
            }

            input[0] = input[1] = input[2];
            input[sampleCount] = input[sampleCount + 1] = input[sampleCount - 1];
        }
    }

//    @Benchmark
    public void baseline(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer.getShort(i0 * 2);
            float p1 = buffer.getShort(i1 * 2);
            float p2 = buffer.getShort(i2 * 2);
            float p3 = buffer.getShort(i3 * 2);

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;
            p2 *= INV_SHORT;
            p3 *= INV_SHORT;

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float sample = 0.5f * (
                    (2 * p1) +
                            (-p0 + p2) * t +
                            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            );

            output[i] = sample;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void lerpReference(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int k = min(j + 1, sampleCount - 1);

            float p0 = buffer.getShort(j);
            float p1 = buffer.getShort(k);

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;

            float interpolatedSample = (1.f - t) * p0 + t * p1;

            output[i] = interpolatedSample;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void accumulateFloatIndex(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final double pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        double x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) (float) x;
            float t = (float) x - j;

            x += pitch;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer.getShort(i0 * 2);
            float p1 = buffer.getShort(i1 * 2);
            float p2 = buffer.getShort(i2 * 2);
            float p3 = buffer.getShort(i3 * 2);

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;
            p2 *= INV_SHORT;
            p3 *= INV_SHORT;

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float sample = 0.5f * (
                    (2 * p1) +
                            (-p0 + p2) * t +
                            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            );

            output[i] = sample;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void floatArray(FloatArrayState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final float[] buffer = state.input;

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer[i0];
            float p1 = buffer[i1];
            float p2 = buffer[i2];
            float p3 = buffer[i3];

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float sample = 0.5f * (
                    (2 * p1) +
                            (-p0 + p2) * t +
                            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            );

            output[i] = sample;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void noIndexClamping(PaddedByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final ByteBuffer buffer = state.input;

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = j + 1;
            int i1 = j + 2;
            int i2 = j + 3;
            int i3 = j + 4;

            float p0 = buffer.getShort(i0 * 2);
            float p1 = buffer.getShort(i1 * 2);
            float p2 = buffer.getShort(i2 * 2);
            float p3 = buffer.getShort(i3 * 2);

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;
            p2 *= INV_SHORT;
            p3 *= INV_SHORT;

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float sample = 0.5f * (
                    (2 * p1) +
                            (-p0 + p2) * t +
                            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            );

            output[i] = sample;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void fma(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer.getShort(i0 * 2);
            float p1 = buffer.getShort(i1 * 2);
            float p2 = buffer.getShort(i2 * 2);
            float p3 = buffer.getShort(i3 * 2);

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;
            p2 *= INV_SHORT;
            p3 *= INV_SHORT;

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float a = Math.fma(-p0 + p2,    t,     2f*p1);
            float b = Math.fma(2f*p0 -5f*p1 +4f*p2 -p3, t2, a);
            float c = Math.fma(-p0 +3f*p1 -3f*p2 +p3,  t3, b);

            output[i] = 0.5f * c;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void moreFMA(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer.getShort(i0 * 2);
            float p1 = buffer.getShort(i1 * 2);
            float p2 = buffer.getShort(i2 * 2);
            float p3 = buffer.getShort(i3 * 2);

            p0 *= INV_SHORT;
            p1 *= INV_SHORT;
            p2 *= INV_SHORT;
            p3 *= INV_SHORT;

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float l = Math.fma(-3, p2, p3 - p0);
            float m = Math.fma(3, p1, l);
            float n = Math.fma(4, p2, -p3);
            float o = Math.fma(-5, p1, n);
            float p = Math.fma(2, p0, o);
            float q = Math.fma(p, t, m * t3);
            float r = Math.fma(-p0 + p2, t, q);
            float s = Math.fma(2, p1, r);

            output[i] = 0.5f * s;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void accumulateFloatArray(FloatArrayState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final double pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final float[] buffer = state.input;

        double x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) (float) x;
            float t = (float) x - j;

            x += pitch;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer[i0];
            float p1 = buffer[i1];
            float p2 = buffer[i2];
            float p3 = buffer[i3];

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float sample = 0.5f * (
                    (2 * p1) +
                            (-p0 + p2) * t +
                            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            );

            output[i] = sample;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void accumulateFloatArrayNoClampFMA(PaddedFloatArrayState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final double pitch = state.pitch;
        final float[] buffer = state.input;

        double x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) (float) x;
            float t = (float) x - j;

            x += pitch;

            int i0 = j + 1;
            int i1 = j + 2;
            int i2 = j + 3;
            int i3 = j + 4;

            float p0 = buffer[i0];
            float p1 = buffer[i1];
            float p2 = buffer[i2];
            float p3 = buffer[i3];

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float a = Math.fma(-p0 + p2,    t,     2f*p1);
            float b = Math.fma(2f*p0 -5f*p1 +4f*p2 -p3, t2, a);
            float c = Math.fma(-p0 +3f*p1 -3f*p2 +p3,  t3, b);

            output[i] = 0.5f * c;
        }

        blackhole.consume(output);
    }

//    @Benchmark
    public void simd(SimdPaddedFloatArrayState state, Blackhole blackhole) {
        final float pitch = state.pitch;
        final float[] xs = state.xs;
        final int length = state.xs.length;

        for (int i = 0; i < length; i++) {
            xs[i] = i * pitch;
        }

        evaluateSimd(length, xs, state.input, state.output);

        blackhole.consume(state.output);
    }

    private static void evaluateSimd(final int len, final float[] xs, final float[] y_in, final float[] y_out) {
        for (int i = 0; i < len; i++) {
            final float x = xs[i];

            final int j = (int) x;
            final float t = x - (float) j;

            float p0 = y_in[j + 1];
            float p1 = y_in[j + 2];
            float p2 = y_in[j + 3];
            float p3 = y_in[j + 4];

            // Catmull-Rom spline formula
            float t2 = t * t;
            float t3 = t2 * t;

            float a = Math.fma(-p0 + p2, t, 2f * p1);
            float b = Math.fma(2f * p0 - 5f * p1 + 4f * p2 - p3, t2, a);
            float c = Math.fma(-p0 + 3f * p1 - 3f * p2 + p3, t3, b);

            y_out[i] = 0.5f * c;
        }
    }

//    @Benchmark
    @Threads(2)
    public void streams(PaddedFloatArrayState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final float[] buffer = state.input;

        IntStream.range(0, length)
                .parallel()
                .forEach(i -> {
                    float x = i * pitch;
                    int j = (int) x;
                    float t = x - j;

                    float p0 = buffer[j + 1];
                    float p1 = buffer[j + 2];
                    float p2 = buffer[j + 3];
                    float p3 = buffer[j + 4];

                    // Catmull-Rom spline formula
                    float t2 = t * t;
                    float t3 = t2 * t;

                    float a = Math.fma(-p0 + p2,    t,     2f*p1);
                    float b = Math.fma(2f*p0 -5f*p1 +4f*p2 -p3, t2, a);
                    float c = Math.fma(-p0 +3f*p1 -3f*p2 +p3,  t3, b);

                    output[i] = 0.5f * c;
                });

        blackhole.consume(buffer);
    }

//    @Benchmark
    @Threads(2)
    public void virtual_threads_simd(SimdPaddedFloatArrayState state, Blackhole blackhole) throws InterruptedException {
        final float pitch = state.pitch;
        final float[] xs = state.xs;
        final int length = state.xs.length;
        float[] input = state.input;
        float[] output = state.output;

        final int n = Runtime.getRuntime().availableProcessors();
        final int batch = length / n;

        Thread[] threads = new Thread[n];

        for (int i = 0; i < n; i++) {
            final int start = i * batch;
            final int len = min(batch, length - start);
            final int end = start + len;

            threads[i] = Thread.startVirtualThread(() -> {
                for (int j = start; j < end; j++) {
                    xs[j] = j * pitch;
                }

                evaluateSimd(len, xs, input, output);
            });
        }

        for (Thread thread : threads) {
            thread.join();
        }

        blackhole.consume(output);
    }
}
