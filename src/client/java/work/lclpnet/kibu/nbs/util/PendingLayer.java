package work.lclpnet.kibu.nbs.util;

import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.impl.ListIndex;

import java.util.Map;

public class PendingLayer implements Layer {

    private final byte volume;
    private final short panning;
    private final Index<Note> notes = new ListIndex<>(Map.of());  // TODO change to a mutable index

    public PendingLayer(byte volume, short panning) {
        this.volume = volume;
        this.panning = panning;
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

    public void setNote(int i, Note note) {
        // TODO implement
    }
}
