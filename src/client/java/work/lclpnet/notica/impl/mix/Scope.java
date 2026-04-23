package work.lclpnet.notica.impl.mix;

import lombok.Getter;

import java.util.Arrays;

/**
 * A scope for sound mixing.
 * In the case of parallel sound mixers, each worker should get its own scope in order to eliminate the need for synchronization.
 * Individual scopes may be combined using {@link #add(Scope)}.
 */
public class Scope {

    /**
     * The buffer for the current sound to mix.
     */
    @Getter
    private final float[] sampleBuffer;
    /**
     * Result ring buffer for the mixed (combined) sample.
     */
    private final float[][] buffers;

    public Scope(int sampleBufferCount, int bufferCount, int bufferSize) {
        sampleBuffer = new float[sampleBufferCount * bufferSize];

        buffers = new float[bufferCount][0];

        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new float[bufferSize];
        }
    }

    public float[] getBuffer(int i) {
        return buffers[i];
    }

    public void copy(Scope scope) {
        final int bufferCount = scope.buffers.length;

        if (bufferCount != this.buffers.length) {
            throw new IllegalArgumentException("Buffer count mismatch");
        }

        for (int i = 0; i < bufferCount; i++) {
            float[] src = scope.buffers[i];
            float[] dst = this.buffers[i];

            System.arraycopy(src, 0, dst, 0, src.length);
        }
    }

    public void add(Scope scope) {
        final int bufferCount = scope.buffers.length;

        if (bufferCount != this.buffers.length) {
            throw new IllegalArgumentException("Buffer count mismatch");
        }

        for (int i = 0; i < bufferCount; i++) {
            final float[] src = scope.buffers[i];
            final float[] dst = this.buffers[i];

            add(src, dst);
        }
    }

    private static void add(float[] src, float[] dst) {
        final int len = src.length;

        if (len != dst.length) {
            throw new IllegalArgumentException("Buffer length mismatch");
        }

        for (int j = 0; j < len; j++) {
            dst[j] += src[j];
        }
    }

    public void reset() {
        resetBuffer(sampleBuffer);

        for (int i = 0; i < buffers.length; i++) {
            resetBuffer(i);
        }
    }

    public void resetBuffer(int buffer) {
        resetBuffer(buffers[buffer]);
    }

    private void resetBuffer(float[] buf) {
        Arrays.fill(buf, 0f);
    }

    public int getBufferCount() {
        return buffers.length;
    }
}
