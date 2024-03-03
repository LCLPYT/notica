package work.lclpnet.kibu.nbs.data;

public record Note(byte instrument, byte key, byte velocity, short panning, short pitch) {
}
