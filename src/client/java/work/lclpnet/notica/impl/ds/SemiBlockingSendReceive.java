package work.lclpnet.notica.impl.ds;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SendReceive} implementation that uses a {@link Semaphore} to bound capacity,
 * while keeping the underlying queue unsynchronized with the blocking call on the sender side.
 * <p>
 * {@link #offer(Object)} blocks up to {@code offerTimeoutMs} milliseconds waiting for a
 * semaphore permit (i.e. for capacity to free up), making it the blocking half.
 * {@link #take()} is non-blocking: it returns {@code null} immediately if the queue is empty,
 * making it the non-blocking half — hence <em>semi</em>-blocking.
 *
 * @param <T> the type of items transferred through this channel
 */
public class SemiBlockingSendReceive<T> implements SendReceive<T> {

    private final Semaphore semaphore;
    private final int offerTimeoutMs;
    private final Queue<T> queue = new LinkedList<>();

    /**
     * Creates a new {@link SemiBlockingSendReceive} with the given capacity and offer timeout.
     *
     * @param capacity       the maximum number of items the channel can hold at once
     * @param offerTimeoutMs the maximum time in milliseconds {@link #offer(Object)} will wait
     *                       for a free slot before returning {@code false}
     */
    public SemiBlockingSendReceive(int capacity, int offerTimeoutMs) {
        semaphore = new Semaphore(capacity, true);
        this.offerTimeoutMs = offerTimeoutMs;
    }

    /**
     * Inserts the given item into the queue, waiting up to {@code offerTimeoutMs} milliseconds
     * for a permit if the channel is at capacity.
     *
     * @param item the item to insert; must not be {@code null}
     * @return {@code true} if the item was accepted, {@code false} if the timeout elapsed or
     *         the underlying queue rejected the item
     * @throws InterruptedException if the calling thread is interrupted while waiting for a permit
     */
    @Override
    public boolean offer(T item) throws InterruptedException {
        if (!semaphore.tryAcquire(offerTimeoutMs, TimeUnit.MILLISECONDS)) {
            return false;
        }

        synchronized (this) {
            if (!queue.offer(item)) {
                semaphore.release();
                return false;
            }

            return true;
        }
    }

    /**
     * Retrieves and removes the head of the queue without blocking.
     * Releases the semaphore permit so a waiting sender may proceed.
     *
     * @return the head item, or {@code null} if the queue is currently empty
     */
    @Override
    public synchronized T take() {
        if (queue.isEmpty()) {
            return null;
        }

        T item = queue.poll();

        semaphore.release();

        return item;
    }

    /**
     * Returns the number of items currently in the queue.
     *
     * @return current queue size
     */
    @Override
    public synchronized int size() {
        return queue.size();
    }

    /**
     * Returns {@code true} if the queue contains no items.
     *
     * @return {@code true} if empty
     */
    @Override
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Removes all items from the queue and releases their semaphore permits,
     * allowing blocked senders to proceed.
     */
    @Override
    public synchronized void clear() {
        int size = queue.size();
        queue.clear();
        semaphore.release(size);
    }
}
