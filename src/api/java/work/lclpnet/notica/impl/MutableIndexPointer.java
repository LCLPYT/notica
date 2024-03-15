package work.lclpnet.notica.impl;

import work.lclpnet.notica.api.IndexPointer;

public class MutableIndexPointer<T> implements IndexPointer<T> {

    private int index = 0;
    private T value = null;

    @Override
    public int index() {
        return index;
    }

    @Override
    public T value() {
        return value;
    }

    public void set(int index, T value) {
        this.index = index;
        this.value = value;
    }
}
