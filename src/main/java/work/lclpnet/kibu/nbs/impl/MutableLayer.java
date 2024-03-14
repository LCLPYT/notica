package work.lclpnet.kibu.nbs.impl;

import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.NoteEvent;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;

public class MutableLayer implements Layer {

    private final byte volume;
    private final short panning;
    private final MutableIndex<Note> notes = new MutableIndex<>();

    public MutableLayer(byte volume, short panning) {
        this.volume = volume;
        this.panning = panning;
    }

    public MutableLayer() {
        this((byte) 100, (short) 100);
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public byte volume() {
        return volume;
    }

    @Override
    public short panning() {
        return panning;
    }

    @Override
    public Index<Note> notes() {
        return notes;
    }

    public void accept(NoteEvent noteEvent) {
        accept(noteEvent.tick(), noteEvent.note());
    }

    public void accept(int tick, Note note) {
        notes.set(tick, note);
    }
}
