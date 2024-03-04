package work.lclpnet.kibu.nbs.data;

import java.util.Map;

public record Layer(String name, byte volume, short panning, Map<Integer, Note> notes) {

    public static Layer of(Map<Integer, Note> notes) {
        return new Layer("", (byte) 100, (byte) 100, notes);
    }
}
