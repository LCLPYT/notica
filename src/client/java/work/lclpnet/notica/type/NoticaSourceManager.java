package work.lclpnet.notica.type;

import org.jetbrains.annotations.Nullable;

public interface NoticaSourceManager {

    void notica$onStopped(@Nullable Runnable runnable);
}
