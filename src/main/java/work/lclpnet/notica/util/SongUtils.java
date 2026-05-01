package work.lclpnet.notica.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.notica.NoticaInit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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

    public static CompletableFuture<Suggestions> suggestSongFiles(
            Path songDirectory, SuggestionsBuilder builder, Logger logger) {
        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.walk(songDirectory, 8)) {
                files.filter(path -> path.getFileName().toString().endsWith(".nbs") && Files.isRegularFile(path))
                        .map(songDirectory::relativize)
                        .map(Path::toString)
                        .map(SongUtils::transformSongPath)
                        .forEach(builder::suggest);
            } catch (IOException e) {
                logger.error("Failed to walk files in songs directory", e);
            }
            return builder.build();
        });
    }

    public static String transformSongPath(String s) {
        s = s.replace('\\', '/');
        boolean needsQuoting = false;

        for (int i = 0, len = s.length(); i < len; i++) {
            if (!StringReader.isAllowedInUnquotedString(s.charAt(i))) {
                needsQuoting = true;
                break;
            }
        }

        return needsQuoting ? '"' + s + '"' : s;
    }
}
