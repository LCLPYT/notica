package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.function.Supplier;

/**
 * Coordinates access to {@link SongStream} buffers across multiple consumers.
 * <p>
 * Each consumer gets its own queue so that bursty or unbalanced access (e.g. Minecraft
 * pre-filling 4 buffers for one stream before touching the other) does not desynchronize
 * the channels. Every {@link ByteBuffer} array produced by the stream is delivered to
 * every consumer exactly once, in order.
 */
public class SharedSongBuffers {

    private final SongStream stream;
    private final int consumerCount;
    private final ArrayDeque<ByteBuffer[]>[] queues;

    @SuppressWarnings("unchecked")
    public SharedSongBuffers(SongStream stream, int consumerCount) {
        this.stream = stream;
        this.consumerCount = consumerCount;
        this.queues = new ArrayDeque[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            queues[i] = new ArrayDeque<>();
        }
    }

    public Supplier<@Nullable ByteBuffer[]> consumerSupplier(int consumerIndex) {
        return () -> next(consumerIndex);
    }

    private synchronized @Nullable ByteBuffer[] next(int consumerIndex) {
        ArrayDeque<ByteBuffer[]> queue = queues[consumerIndex];

        if (!queue.isEmpty()) {
            return queue.poll();
        }

        ByteBuffer[] buffers = stream.nextBuffers();

        if (buffers == null) return null;

        for (int i = 0; i < consumerCount; i++) {
            if (i != consumerIndex) {
                queues[i].add(buffers);
            }
        }

        return buffers;
    }
}
