package work.lclpnet.kibu.nbs.data;

public record Instruments(CustomInstrument[] custom, int customBegin) {

    public static final Instruments DEFAULT = new Instruments(new CustomInstrument[0], 16);
}
