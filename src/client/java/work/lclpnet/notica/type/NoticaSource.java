package work.lclpnet.notica.type;

import com.mojang.blaze3d.audio.Channel;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface NoticaSource {

    void notica$setNoticaSource();

    void notica$setStopped();

    void notica$onTick(@Nullable Consumer<Channel> action);

    float notica$getOffsetSeconds();

    int notica$getCompletedBuffers();
}
