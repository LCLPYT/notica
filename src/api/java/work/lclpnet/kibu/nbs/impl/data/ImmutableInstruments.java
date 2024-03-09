package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.Instruments;

public record ImmutableInstruments(ImmutableCustomInstrument[] custom, int customBegin) implements Instruments {
    public static final ImmutableInstruments DEFAULT = new ImmutableInstruments(new ImmutableCustomInstrument[0], 16);
}
