package work.lclpnet.kibu.nbs.impl;

import work.lclpnet.kibu.nbs.api.NoteEvent;
import work.lclpnet.kibu.nbs.api.data.Note;

public class MutableNoteEvent implements NoteEvent {

    private int tick = 0;
    private int layer = 0;
    private Note note = null;

    @Override
    public int tick() {
        return tick;
    }

    @Override
    public int layer() {
        return layer;
    }

    @Override
    public Note note() {
        return note;
    }

    public void set(int tick, int layer, Note note) {
        this.tick = tick;
        this.layer = layer;
        this.note = note;
    }

    @Override
    public String toString() {
        return "MutableNoteEvent{tick=%d, layer=%d, note=%s}".formatted(tick, layer, note);
    }
}
