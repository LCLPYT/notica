package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;

@SuppressWarnings({"FieldMayBeFinal", "DuplicatedCode"})
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Threads(6)
public class CatmullRomSplineBenchmark {

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
    public static class ShortArrayState {

        int sampleCount;
        float pitch;
        short[] input;
        float[] output;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = new short[sampleCount];
            output = new float[(int) (sampleCount / pitch)];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);

                input[i] = (short) (sin * Short.MAX_VALUE);
            }
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

    @Benchmark
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

    @Benchmark
    public void accumulateFloatIndex(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final double pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        double x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) x;
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

    @Benchmark
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

    @Benchmark
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

    @Benchmark
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

    @Benchmark
    public void accumulateFloatArray(FloatArrayState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final double pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final float[] buffer = state.input;

        double x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) x;
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

    @Benchmark
    public void accumulateFloatArrayNoClampFMA(PaddedFloatArrayState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final double pitch = state.pitch;
        final float[] buffer = state.input;

        double x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) x;
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
}
