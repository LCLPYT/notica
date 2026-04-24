package work.lclpnet.notica.impl.ds;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SendReceive} implementation backed by a {@link BlockingQueue}.
 * <p>
 * Both {@link #offer(Object)} and {@link #take()} are blocking operations:
 * {@link #offer(Object)} waits up to {@code offerTimeoutMs} milliseconds for capacity,
 * while {@link #take()} blocks indefinitely until an item becomes available.
 *
 * @param <T> the type of items transferred through this channel
 */
public class BlockingSendReceive<T> implements SendReceive<T> {

    private final BlockingQueue<T> queue;
    private final int offerTimeoutMs;

    /**
     * Creates a new {@link BlockingSendReceive} with the given capacity and offer timeout.
     *
     * @param capacity      the maximum number of items the queue can hold
     * @param offerTimeoutMs the maximum time in milliseconds to wait when offering an item
     *                       before giving up
     */
    public BlockingSendReceive(int capacity, int offerTimeoutMs) {
        queue = new LinkedBlockingQueue<>(capacity);
        this.offerTimeoutMs = offerTimeoutMs;
    }

    /**
     * Inserts the given item into the queue, waiting up to {@code offerTimeoutMs} milliseconds
     * for space to become available.
     *
     * @param item the item to insert; must not be {@code null}
     * @return {@code true} if the item was accepted, {@code false} if the timeout elapsed
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    @Override
    public boolean offer(T item) throws InterruptedException {
        return queue.offer(item, offerTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieves and removes the head of the queue, blocking until an item is available.
     *
     * @return the head item; never {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    @Override
    public T take() throws InterruptedException {
        return queue.take();
    }

    /**
     * Returns the number of items currently in the queue.
     *
     * @return current queue size
     */
    @Override
    public int size() {
        return queue.size();
    }

    /**
     * Returns {@code true} if the queue contains no items.
     *
     * @return {@code true} if empty
     */
    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Removes all items from the queue.
     */
    @Override
    public void clear() {
        queue.clear();
    }
}
