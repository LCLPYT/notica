package work.lclpnet.notica.api.data;

import work.lclpnet.notica.api.Index;

public interface NoteContainer {

    /**
     * @return The notes of this layer, by song tick.
     */
    Index<? extends Note> notes();
}
