package work.lclpnet.kibu.nbs.data;

import java.util.Map;

public record Layer(String name, byte volume, short panning, Map<Integer, Note> notes) {
}
