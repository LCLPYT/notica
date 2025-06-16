package work.lclpnet.notica.type;

import java.util.function.BooleanSupplier;

public interface NoticaMusicTracker {

    void notica$setMusicInhibitor(BooleanSupplier inhibitor);
}
