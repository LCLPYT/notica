package work.lclpnet.kibu.nbs.api;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

public interface SongResolver {

    @Nullable
    Song resolve(SongDescriptor descriptor);

    @Nullable
    Song resolve(Identifier id);
}
