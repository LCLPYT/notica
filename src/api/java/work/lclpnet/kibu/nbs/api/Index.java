package work.lclpnet.kibu.nbs.api;

import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Index<T> extends Iterable<T> {

    @Nullable
    T get(int i);

    OptionalInt index(T value);

    int size();

    Iterable<IndexPointer<T>> iterateOrdered();

    default Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliterator(iterator(), size(), 0), false);
    }

    default IntStream streamKeysOrdered() {
        return StreamSupport.stream(Spliterators.spliterator(iterateOrdered().iterator(), size(), 0), false)
                .mapToInt(IndexPointer::index);
    }

    default Iterable<Integer> keysOrdered() {
        return () -> streamKeysOrdered().iterator();
    }
}
