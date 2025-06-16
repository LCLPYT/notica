package work.lclpnet.notica.impl.ds;

public interface SendReceive<T> {

    boolean offer(T item) throws InterruptedException;

    T take() throws InterruptedException;

    int size();

    boolean isEmpty();

    void clear();
}
