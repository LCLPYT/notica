package work.lclpnet.kibu.nbs.api;

public interface SongSlice extends Iterable<NoteEvent> {

    int tickStart();

    int tickEnd();

    int layerStart();

    int layerEnd();
}
