package work.lclpnet.notica.impl.ds;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SemiBlockingSendReceive<T> implements SendReceive<T> {

    private final Semaphore semaphore;
    private final int offerTimeoutMs;
    private final Queue<T> queue = new LinkedList<>();

    public SemiBlockingSendReceive(int capacity, int offerTimeoutMs) {
        semaphore = new Semaphore(capacity, true);
        this.offerTimeoutMs = offerTimeoutMs;
    }

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

    @Override
    public synchronized T take() {
        if (queue.isEmpty()) {
            return null;
        }

        T item = queue.poll();

        semaphore.release();

        return item;
    }

    @Override
    public synchronized int size() {
        return queue.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public synchronized void clear() {
        int size = queue.size();
        queue.clear();
        semaphore.release(size);
    }
}
