package work.lclpnet.kibu.nbs.api;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface SongSlice extends Iterable<NoteEvent> {

    int tickStart();

    int tickEnd();

    int layerStart();

    int layerEnd();

    default Stream<NoteEvent> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
