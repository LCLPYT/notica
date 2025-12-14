package work.lclpnet.notica.type;

import org.jetbrains.annotations.Nullable;

public interface NoticaChannelHandle {

    void notica$onStopped(@Nullable Runnable runnable);
}
