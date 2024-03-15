package work.lclpnet.notica.impl.data;

import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;

public record ImmutableInstruments(CustomInstrument[] custom, int customBegin) implements Instruments {
    public static final ImmutableInstruments DEFAULT = new ImmutableInstruments(new ImmutableCustomInstrument[0], 16);
}
