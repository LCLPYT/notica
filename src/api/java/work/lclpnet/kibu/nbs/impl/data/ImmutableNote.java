package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.Note;

public record ImmutableNote(byte instrument, byte key, byte velocity, short panning, short pitch) implements Note {

    public static ImmutableNote of(int instrument, int key) {
        return of(instrument, key, 100, 100, 0);
    }

    public static ImmutableNote of(int instrument, int key, int velocity, int panning, int pitch) {
        return new ImmutableNote((byte) instrument, (byte) key, (byte) velocity, (short) panning, (short) pitch);
    }
}
