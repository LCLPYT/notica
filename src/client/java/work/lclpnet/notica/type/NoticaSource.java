package work.lclpnet.notica.type;

import net.minecraft.client.sound.Source;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface NoticaSource {

    void notica$setNoticaSource();

    void notica$setStopped();

    void notica$onTick(@Nullable Consumer<Source> action);

    float notica$getOffsetSeconds();

    int notica$getCompletedBuffers();
}
