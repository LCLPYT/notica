package work.lclpnet.kibu.nbs.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.KibuNbsInit;
import work.lclpnet.kibu.nbs.controller.Controller;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;
import work.lclpnet.kibu.nbs.util.PlayerConfigContainer;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.RootText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

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
                                .executes(this::playSongAutoSelf)
                                .then(argument("id", IdentifierArgumentType.identifier())
                                        .executes(this::playSongIdSelf))))
                .then(literal("stop")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(this::stopAllSelf)
                        .then(argument("id", IdentifierArgumentType.identifier())
                                .suggests(this::playingSongIds)
                                .executes(this::stopSongSelf)))
                .then(literal("set")
                        .then(literal("extended_range")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(this::changeExtendedRange)))
                        .then(literal("volume")
                                .then(argument("percent", FloatArgumentType.floatArg(0f, 100f))
                                        .executes(this::changeVolume))));
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

        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(enabled);
        configs.saveConfig(player);

        String key = enabled ? "kibu-nbs.music.extended_octaves.enabled" : "kibu-nbs.music.extended_octaves.disabled";
        Formatting color = enabled ? GREEN : RED;

        var msg = translations.translateText(player, key).formatted(color);

        player.sendMessage(msg);

        return 1;
    }

    private int changeVolume(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        float percent = FloatArgumentType.getFloat(ctx, "percent");

        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());

        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setVolume(percent / 100);
        configs.saveConfig(player);

        var msg = translations.translateText(player, "kibu-nbs.music.volume.changed",
                styled("%.0f%%".formatted(percent), YELLOW)).formatted(GREEN);

        player.sendMessage(msg);

        return 1;
    }

    private int playSongAutoSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String songFile = StringArgumentType.getString(ctx, "song");

        Path path = songDirectory.resolve(songFile);
        Identifier id = createSongId(path);

        return playSongSelf(player, path, id);
    }

    private int playSongIdSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String songFile = StringArgumentType.getString(ctx, "song");
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        Path path = songDirectory.resolve(songFile);

        return playSongSelf(player, path, id);
    }

    private int playSongSelf(ServerPlayerEntity player, Path path, Identifier id) {
        Path relativePath = songDirectory.relativize(path);
        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());

        instance.loadSongFile(path, id).exceptionally(error -> {
            RootText msg;

            if (error instanceof CompletionException c && c.getCause() instanceof NoSuchFileException) {
                msg = translations.translateText(player, "kibu-nbs.music.play.not_found", styled(relativePath, YELLOW));
            } else {
                msg = translations.translateText(player, "kibu-nbs.music.play.error");
            }

            msg.formatted(RED);

            player.sendMessage(msg);

            logger.error("Failed to load song file", error);
            return null;
        }).thenAccept(descriptor -> {
            if (descriptor == null) return;

            var msg = translations.translateText(player, "kibu-nbs.music.play", styled(relativePath, YELLOW))
                    .formatted(GREEN);

            player.sendMessage(msg);

            instance.execute(List.of(player), controller -> controller.playSong(descriptor, 1f));
        });

        return 1;
    }

    private int stopAllSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());
        instance.execute(List.of(player), controller -> {
            var playing = controller.getPlayingSongs();
            int count = playing.size();

            for (SongDescriptor song : playing) {
                controller.stopSong(song);
            }

            RootText msg;

            if (count == 0) {
                msg = translations.translateText(player, "kibu-nbs.music.none_playing").formatted(RED);
            } else {
                msg = translations.translateText(player, "kibu-nbs.music.stopped.all").formatted(GREEN);
            }

            player.sendMessage(msg);
        });

        return 0;
    }

    private int stopSongSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());
        instance.execute(List.of(player), controller -> controller.getPlayingSongById(id).ifPresentOrElse(song -> {
            controller.stopSong(song);

            var msg = translations.translateText(player, "kibu-nbs.music.stopped", styled(id, YELLOW))
                    .formatted(GREEN);

            player.sendMessage(msg);
        }, () -> {
            var msg = translations.translateText(player, "kibu-nbs.music.not_playing", styled(id, YELLOW))
                    .formatted(RED);

            player.sendMessage(msg);
        }));

        return 0;
    }

    private CompletableFuture<Suggestions> availableSongFiles(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.walk(songDirectory, 8)){
                files.filter(path -> path.getFileName().toString().endsWith(".nbs") && Files.isRegularFile(path))
                        .map(songDirectory::relativize)
                        .map(Path::toString)
                        .map(MusicCommand::transformString)
                        .forEach(builder::suggest);
            } catch (IOException e) {
                logger.error("Failed to walk files in songs directory", e);
            }

            return builder.build();
        });
    }

    private CompletableFuture<Suggestions> playingSongIds(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();

        if (player == null) return builder.buildFuture();

        Controller controller = KibuNbsApiImpl.getInstance(player.getServer()).getController(player);

        controller.getPlayingSongs().stream()
                .map(SongDescriptor::id)
                .map(Identifier::toString)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private static String transformString(String s) {
        s = s.replace('\\', '/');

        if (s.indexOf(' ') >= 0 || s.indexOf('/') >= 0) {
            s = '"' + s + '"';
        }

        return s;
    }

    @NotNull
    private static Identifier createSongId(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        // remove file extension
        int idx = name.lastIndexOf('.');

        if (idx >= 0) {
            name = name.substring(0, idx);
        }

        // remove invalid characters
        name = name.replaceAll("[^a-z0-9/._-]", "");

        return KibuNbsInit.identifier(name);
    }
}
