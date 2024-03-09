package work.lclpnet.kibu.nbs.api.data;

import work.lclpnet.kibu.nbs.api.Index;

public interface NoteContainer {

    /**
     * @return The notes of this layer, by song tick.
     */
    Index<? extends Note> notes();
}
