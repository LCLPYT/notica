package work.lclpnet.notica.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.Index;
import work.lclpnet.notica.api.IndexPointer;

import java.util.*;
import java.util.function.Function;

import static java.lang.Math.max;

public class MutableIndex<T> implements Index<T> {

    private final Map<Integer, T> map = new HashMap<>();
    private final Map<T, Integer> indexMap = new HashMap<>();
    private int maxIndex = -1;

    @Override
    public @Nullable T get(int i) {
        synchronized (this) {
            return map.get(i);
        }
    }

    @Override
    public OptionalInt index(T value) {
        Integer i;

        synchronized (this) {
            i = indexMap.get(value);
        }

        if (i == null) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(i);
    }

    @Override
    public int size() {
        synchronized (this) {
            return map.size();
        }
    }

    @Override
    public Iterable<IndexPointer<T>> iterateOrdered() {
        var parent = iterator();

        return () -> new Iterator<>() {
            private final MutableIndexPointer<T> pointer = new MutableIndexPointer<>();

            @Override
            public boolean hasNext() {
                return parent.hasNext();
            }

            @Override
            public IndexPointer<T> next() {
                T next = parent.next();
                int index = indexMap.get(next);

                pointer.set(index, next);

                return pointer;
            }
        };
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return map.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .iterator();
    }

    @Override
    public OptionalInt maxIndex() {
        return maxIndex >= 0 ? OptionalInt.of(maxIndex) : OptionalInt.empty();
    }

    public synchronized void set(int i, T value) {
        if (value == null) {
            value = map.remove(i);

            if (value == null) return;

            indexMap.remove(value);

            maxIndex = map.keySet().stream().mapToInt(idx -> idx).max().orElse(-1);
            return;
        }

        T oldValue = map.put(i, value);

        if (oldValue != null) {
            indexMap.remove(oldValue);
        }

        indexMap.put(value, i);
        maxIndex = max(maxIndex, i);
    }

    public T computeIfAbsent(int i, Function<Integer, T> mappingFunction) {
        synchronized (this) {
            T val = map.get(i);

            if (val != null) return val;

            val = mappingFunction.apply(i);

            set(i, val);

            return val;
        }
    }
}
