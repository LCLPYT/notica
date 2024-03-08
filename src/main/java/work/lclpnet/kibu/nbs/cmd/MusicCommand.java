package work.lclpnet.kibu.nbs.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;
import work.lclpnet.kibu.translate.TranslationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.util.Formatting.GREEN;
import static net.minecraft.util.Formatting.RED;

public class MusicCommand {

    private final Path songDirectory;
    private final TranslationService translations;
    private final Logger logger;

    public MusicCommand(Path songDirectory, TranslationService translations, Logger logger) {
        this.songDirectory = songDirectory;
        this.translations = translations;
        this.logger = logger;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("music")
                .then(literal("play")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(argument("song", StringArgumentType.string())
                                .suggests(this::availableSongFiles)
                                .then(argument("id", IdentifierArgumentType.identifier())
                                        .executes(this::playSongSelf))))
                .then(literal("set")
                        .then(literal("extended_range")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(this::changeExtendedRange))));
    }

    private int changeExtendedRange(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());

        if (instance.hasModInstalled(player)) {
            // extended octave range is automatically supported by the mod on the client
            var msg = translations.translateText(player, "kibu-nbs.music.extended_octaves.mod").formatted(RED);
            player.sendMessage(msg);
            return 0;
        }

        instance.getExtendedOctaveRangeSupport().setSupported(player, enabled);

        String key = enabled ? "kibu-nbs.music.extended_octaves.enabled" : "kibu-nbs.music.extended_octaves.disabled";
        Formatting color = enabled ? GREEN : RED;

        var msg = translations.translateText(player, key).formatted(color);

        player.sendMessage(msg);

        return 1;
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
                        .map(MusicCommand::addQuotes)
                        .forEach(builder::suggest);
            } catch (IOException e) {
                logger.error("Failed to walk files in songs directory", e);
            }

            return builder.build();
        });
    }

    private static String addQuotes(String s) {
        if (s.indexOf(' ') == -1) return s;

        return '"' + s + '"';
    }
}
