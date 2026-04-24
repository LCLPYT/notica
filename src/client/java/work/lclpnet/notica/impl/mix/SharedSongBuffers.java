package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Coordinates access to {@link SongStream} buffers across multiple consumers.
 * <p>
 * Each call to {@link #next()} returns the same {@link ByteBuffer} array until all
 * {@code consumerCount} consumers have read from it, then fetches the next one.
 * This ensures that e.g. left and right mono channels read from the same stereo buffer pair.
 */
public class SharedSongBuffers {

    private final SongStream stream;
    private final int consumerCount;
    private ByteBuffer[] current = null;
    private int remaining = 0;

    public SharedSongBuffers(SongStream stream, int consumerCount) {
        this.stream = stream;
        this.consumerCount = consumerCount;
    }

    public synchronized @Nullable ByteBuffer[] next() {
        if (remaining <= 0) {
            current = stream.nextBuffers();
            remaining = consumerCount;
        }
        remaining--;
        return current;
    }
}
