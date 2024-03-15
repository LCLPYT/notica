package work.lclpnet.notica.api;

import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;

public interface NotePlayer {

    void playNote(Song song, Layer layer, Note note);
}
