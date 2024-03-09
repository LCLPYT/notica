package work.lclpnet.kibu.nbs.api;

import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;

public interface NotePlayer {

    void playNote(Song song, Layer layer, Note note);
}
