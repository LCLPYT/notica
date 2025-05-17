package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
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
    public static class DirectByteBufferState {

        int sampleCount;
        float pitch;
        ByteBuffer input;
        float[] output;

        @Setup(Level.Trial)
        public void setup() {
            sampleCount = 48_000 * 5;
            pitch = 0.7f;
            input = ByteBuffer.allocateDirect(2 * sampleCount);
            output = new float[(int) (sampleCount / pitch)];

            double offset = 3.629278926426345;
            double phase = 0.0743434;

            for (int i = 0; i < sampleCount; i++) {
                double sin = Math.sin(i * phase + offset);

                input.putShort((short) (sin * Short.MAX_VALUE));
            }
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
    public void noInt2FloatConversion(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        final int pitchInt = (int) pitch;
        final float pitchFrac = pitch - pitchInt;

        int idx = 0;
        float frac = 0.f;

        for (int i = 0; i < length; i++) {
            int j = idx;
            float t = frac;

            frac += pitchFrac;
            idx += pitchInt;

            if (frac >= 1.0f) {
                frac -= 1.0f;
                ++idx;
            }

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
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ByteBuffer buffer = state.input;

        float x = 0f;

        for (int i = 0; i < length; i++) {
            int j = (int) x;
            float t = x - j;

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
    public void shortBuffer(ByteBufferState state, Blackhole blackhole) {
        final float[] output = state.output;
        final int length = output.length;
        final float pitch = state.pitch;
        final int sampleCount = state.sampleCount;
        final ShortBuffer buffer = state.input.asShortBuffer();

        for (int i = 0; i < length; i++) {
            float x = i * pitch;
            int j = (int) x;
            float t = x - j;

            int i0 = max(0, min(j - 1, sampleCount - 1));
            int i1 = max(0, min(j, sampleCount - 1));
            int i2 = max(0, min(j + 1, sampleCount - 1));
            int i3 = max(0, min(j + 2, sampleCount - 1));

            float p0 = buffer.get(i0);
            float p1 = buffer.get(i1);
            float p2 = buffer.get(i2);
            float p3 = buffer.get(i3);

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
    public void asDirectBuffer(DirectByteBufferState state, Blackhole blackhole) {
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
}
