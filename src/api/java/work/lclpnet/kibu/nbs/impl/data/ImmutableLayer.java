package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;

import java.util.Map;

public record ImmutableLayer(String name, byte volume, short panning, Map<Integer, Note> notes) implements Layer {

    public static ImmutableLayer of(Map<Integer, Note> notes) {
        return new ImmutableLayer("", (byte) 100, (byte) 100, notes);
    }
}
