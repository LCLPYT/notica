package work.lclpnet.notica.api;

import work.lclpnet.notica.api.data.Note;

public interface NoteEvent {

    int tick();

    int layer();

    Note note();
}
