package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.Note;

public record ImmutableNote(byte instrument, byte key, byte velocity, short panning, short pitch) implements Note {
}
