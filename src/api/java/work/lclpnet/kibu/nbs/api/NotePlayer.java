package work.lclpnet.kibu.nbs.api;

import work.lclpnet.kibu.nbs.data.Layer;
import work.lclpnet.kibu.nbs.data.Note;
import work.lclpnet.kibu.nbs.data.Song;

public interface NotePlayer {

    void playNote(Song song, Layer layer, Note note);
}
