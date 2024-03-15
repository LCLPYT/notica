package work.lclpnet.notica.api.data;

import org.jetbrains.annotations.Nullable;

public interface Instruments {

    /**
     * @return The custom instruments array.
     */
    CustomInstrument[] custom();

    /**
     * @return The beginning index of the custom instruments.
     */
    int customBegin();

    /**
     * Get a custom instrument by index.
     * @param instrument The instrument index.
     * @return The custom instrument at that index, or null if the instrument is vanilla or does not exist.
     */
    @Nullable
    default CustomInstrument custom(byte instrument) {
        int customBegin = customBegin();

        if (instrument < customBegin) {
            // vanilla instrument
            return null;
        }

        int index = instrument - customBegin;
        var custom = custom();

        if (index >= custom.length) return null;

        return custom[index];
    }
}
