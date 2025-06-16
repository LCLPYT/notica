package work.lclpnet.notica.impl.ds;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BlockingSendReceive<T> implements SendReceive<T> {

    private final BlockingQueue<T> queue;
    private final int offerTimeoutMs;

    public BlockingSendReceive(int capacity, int offerTimeoutMs) {
        queue = new LinkedBlockingQueue<>(capacity);
        this.offerTimeoutMs = offerTimeoutMs;
    }

    @Override
    public boolean offer(T item) throws InterruptedException {
        return queue.offer(item, offerTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public T take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
