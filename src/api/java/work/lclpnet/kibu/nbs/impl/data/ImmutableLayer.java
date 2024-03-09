package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;

public record ImmutableLayer(String name, byte volume, short panning, Index<Note> notes) implements Layer {

    public static ImmutableLayer of(Index<Note> notes) {
        return new ImmutableLayer("", (byte) 100, (byte) 100, notes);
    }
}
