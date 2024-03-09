package work.lclpnet.kibu.nbs.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.IndexPointer;

import java.util.*;

public class ListIndex<T> implements Index<T> {

    private final int[] indexMapping;
    private final int[] reverseIndexMapping;
    private final List<T> items;
    private final int minIndex;

    public ListIndex(Map<Integer, ? extends T> map) {
        int minIndex = 0, maxIndex = -1;

        for (Integer i : map.keySet()) {
            if (i == null) continue;

            if (i > maxIndex) {
                maxIndex = i;
            }

            if (i < minIndex) {
                minIndex = i;
            }
        }

        this.minIndex = minIndex;

        int width = maxIndex - minIndex + 1;
        this.indexMapping = new int[width];
        this.reverseIndexMapping = new int[map.size()];

        int i = 0;
        for (int j = 0; j < width; j++) {
            T value = map.get(j + minIndex);

            if (value == null) {
                indexMapping[j] = -1;
            } else {
                reverseIndexMapping[i] = j;
                indexMapping[j] = i++;
            }
        }

        this.items = map.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .<T>map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public @Nullable T get(int i) {
        int mappedIndex = mapIndex(i);

        if (mappedIndex == -1) {
            return null;
        }

        return items.get(mappedIndex);
    }

    @Override
    public OptionalInt index(T value) {
        int idx = items.indexOf(value);

        if (idx == -1) {
            return OptionalInt.empty();
        }

        idx = unmapIndex(idx);

        if (idx < minIndex) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(idx);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public Iterable<Integer> keys() {
        return () -> Arrays.stream(reverseIndexMapping).iterator();
    }

    @Override
    public Iterable<IndexPointer<T>> iterate() {
        return IndexIterator::new;
    }

    private int mapIndex(int i) {
        i -= minIndex;

        if (i < 0 || i >= indexMapping.length) {
            return -1;
        }

        return indexMapping[i];
    }

    private int unmapIndex(int i) {
        if (i < 0 || i >= reverseIndexMapping.length) {
            i = -1;
        } else {
            i = reverseIndexMapping[i];
        }

        return i + minIndex;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    @Override
    public String toString() {
        return "ListIndex(%s)".formatted(items.size());
    }

    private class IndexIterator implements Iterator<IndexPointer<T>> {

        private final MutableIndexPointer<T> pointer = new MutableIndexPointer<>();
        private boolean hasNext = false, done = false;
        private int nextIndex = 0;

        @Override
        public boolean hasNext() {
            if (!hasNext) {
                advance();
            }

            return !done;
        }

        private void advance() {
            int index = nextIndex++;

            if (index >= items.size()) {
                done = true;
                return;
            }

            int unmappedIndex = unmapIndex(index);

            pointer.set(unmappedIndex, items.get(index));
            hasNext = true;
        }

        @Override
        public IndexPointer<T> next() {
            hasNext = false;
            return pointer;
        }
    }
}
