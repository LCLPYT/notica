package work.lclpnet.notica.util;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.notica.NoticaInit;

import java.nio.file.Path;
import java.util.Locale;

public class SongUtils {

    @NotNull
    public static Identifier createSongId(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        // remove file extension
        int idx = name.lastIndexOf('.');

        if (idx >= 0) {
            name = name.substring(0, idx);
        }

        // remove invalid characters
        name = name.replaceAll("[^a-z0-9/._-]", "");

        return NoticaInit.identifier(name);
    }
}
