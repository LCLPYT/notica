package work.lclpnet.kibu.nbs.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MusicCommand {

    private final Path songDirectory;
    private final Logger logger;

    public MusicCommand(Path songDirectory, Logger logger) {
        this.songDirectory = songDirectory;
        this.logger = logger;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("music")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("play")
                        .then(argument("song", StringArgumentType.string())
                                .suggests(this::availableSongFiles)
                                .then(argument("id", IdentifierArgumentType.identifier())
                                        .executes(this::playSongSelf))));
    }

    private int playSongSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String songFile = StringArgumentType.getString(ctx, "song");
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        Path path = songDirectory.resolve(songFile);

        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());
        instance.loadSongFile(path, id)
                .exceptionally(error -> {
                    logger.error("Failed to load song file", error);
                    return null;
                })
                .thenAccept(descriptor -> {
                    if (descriptor == null) return;

                    instance.execute(List.of(player), controller -> controller.playSong(descriptor, 1f));
                });

        return 1;
    }

    private CompletableFuture<Suggestions> availableSongFiles(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.walk(songDirectory, 8)){
                files.filter(path -> path.getFileName().toString().endsWith(".nbs") && Files.isRegularFile(path))
                        .map(songDirectory::relativize)
                        .map(Path::toString)
                        .forEach(builder::suggest);
            } catch (IOException e) {
                logger.error("Failed to walk files in songs directory", e);
            }

            return builder.build();
        });
    }
}
