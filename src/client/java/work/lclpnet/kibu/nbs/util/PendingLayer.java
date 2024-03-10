package work.lclpnet.kibu.nbs.util;

import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.impl.ListIndex;

import java.util.HashMap;
import java.util.Map;

public class PendingLayer implements Layer {

    private final byte volume;
    private final short panning;
    private ListIndex<Note> notes = new ListIndex<>(Map.of());

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

    public void merge(Index<? extends Note> notes, int offset) {
        Map<Integer, Note> merged = new HashMap<>(this.notes.size() + notes.size());

        for (var pointer : this.notes.iterate()) {
            merged.put(pointer.index(), pointer.value());
        }

        for (var pointer : notes.iterate()) {
            merged.put(pointer.index() + offset, pointer.value());
        }

        this.notes = new ListIndex<>(merged);
    }
}
