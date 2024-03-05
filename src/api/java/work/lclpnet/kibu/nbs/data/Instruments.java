package work.lclpnet.kibu.nbs.data;

import org.jetbrains.annotations.Nullable;

public record Instruments(CustomInstrument[] custom, int customBegin) {

    public static final Instruments DEFAULT = new Instruments(new CustomInstrument[0], 16);

    @Nullable
    public CustomInstrument custom(byte instrument) {
        if (instrument < customBegin) {
            // vanilla instrument
            return null;
        }

        int index = instrument - customBegin;

        if (index >= custom.length) return null;

        return custom[index];
    }
}
