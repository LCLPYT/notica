package work.lclpnet.notica.impl.ds;

/**
 * A bounded, generic channel for transferring items between a producer and a consumer.
 * <p>
 * Implementations define their own blocking semantics: {@link #offer(Object)} typically blocks
 * when the channel is full, and {@link #take()} may block until an item is available or return
 * {@code null} immediately — see the concrete implementation for details.
 *
 * @param <T> the type of items transferred through this channel
 */
public interface SendReceive<T> {

    /**
     * Inserts the given item into the channel.
     * Implementations may block for a bounded duration if the channel is at capacity.
     *
     * @param item the item to insert; must not be {@code null}
     * @return {@code true} if the item was accepted; {@code false} if it was rejected
     *         (e.g. because the timeout elapsed or the channel is closed)
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    boolean offer(T item) throws InterruptedException;

    /**
     * Retrieves and removes an item from the channel.
     * Implementations may block until an item is available, or return {@code null} immediately
     * if the channel is empty — see the concrete implementation for details.
     *
     * @return the retrieved item, or {@code null} if no item was available and the
     *         implementation does not block
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    T take() throws InterruptedException;

    /**
     * Returns the number of items currently held in the channel.
     *
     * @return current item count
     */
    int size();

    /**
     * Returns {@code true} if the channel contains no items.
     *
     * @return {@code true} if empty
     */
    boolean isEmpty();

    /**
     * Removes all items from the channel, resetting it to an empty state.
     * Any capacity that was consumed by the removed items is released.
     */
    void clear();
}
