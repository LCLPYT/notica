package work.lclpnet.kibu.nbs.api;

import work.lclpnet.kibu.nbs.api.data.Note;

public interface NoteEvent {

    int tick();

    int layer();

    Note note();
}
