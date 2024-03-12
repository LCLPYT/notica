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
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.KibuNbsApi;
import work.lclpnet.kibu.nbs.KibuNbsInit;
import work.lclpnet.kibu.nbs.api.SongHandle;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;
import work.lclpnet.kibu.nbs.util.PlayerConfigContainer;
import work.lclpnet.kibu.nbs.util.ServerSongLoader;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.RootText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
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
                                .then(argument("listeners", EntityArgumentType.players())
                                        .executes(this::playSongAuto)
                                        .then(argument("id", IdentifierArgumentType.identifier())
                                                .executes(this::playSongId)))))
                .then(literal("stop")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(this::stopAllSelf)
                        .then(argument("listeners", EntityArgumentType.players())
                                .executes(this::stopAll)
                                .then(argument("id", IdentifierArgumentType.identifier())
                                        .suggests(this::commonPlayingSongIds)
                                        .executes(this::stopSong))))
                .then(literal("set")
                        .then(literal("extended_range")
                                .requires(this::isVanillaPlayer)
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
        instance.syncPlayerConfig(player);

        var msg = translations.translateText(player, "kibu-nbs.music.volume.changed",
                styled("%.0f%%".formatted(percent), YELLOW)).formatted(GREEN);

        player.sendMessage(msg);

        return 1;
    }

    private int playSongAutoSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String songFile = StringArgumentType.getString(ctx, "song");

        Path path = songDirectory.resolve(songFile);
        Identifier id = createSongId(path);

        return playSong(source, List.of(player), path, id);
    }

    private int playSongAuto(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");
        String songFile = StringArgumentType.getString(ctx, "song");

        Path path = songDirectory.resolve(songFile);
        Identifier id = createSongId(path);

        return playSong(ctx.getSource(), listeners, path, id);
    }

    private int playSongId(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");
        String songFile = StringArgumentType.getString(ctx, "song");
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        Path path = songDirectory.resolve(songFile);

        return playSong(ctx.getSource(), listeners, path, id);
    }

    private int playSong(ServerCommandSource source, Collection<? extends ServerPlayerEntity> listeners, Path path, Identifier id) {
        Path relativePath = songDirectory.relativize(path);

        CompletableFuture.supplyAsync(() -> {
            try (var in = Files.newInputStream(path)) {
                return ServerSongLoader.load(in, id);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).exceptionally(error -> {
            RootText msg;

            if (error instanceof CompletionException c && c.getCause() instanceof NoSuchFileException) {
                msg = translations.translateText(source, "kibu-nbs.music.play.not_found", styled(relativePath, YELLOW));
            } else {
                msg = translations.translateText(source, "kibu-nbs.music.play.error");
            }

            msg.formatted(RED);

            source.sendMessage(msg);

            logger.error("Failed to load song file", error);
            return null;
        }).thenAccept(song -> {
            if (song == null) return;

            var msg = translations.translateText(source, "kibu-nbs.music.play", styled(relativePath, YELLOW))
                    .formatted(GREEN);

            source.sendMessage(msg);

            KibuNbsApi api = KibuNbsApi.getInstance(source.getServer());
            api.playSong(song, 1f, listeners);
        });

        return 1;
    }

    private int stopAllSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        KibuNbsApi api = KibuNbsApi.getInstance(player.getServer());
        var handles = api.getPlayingSongs(player);
        boolean empty = handles.isEmpty();

        for (SongHandle handle : handles) {
            handle.remove(player);
        }

        RootText msg;

        if (empty) {
            msg = translations.translateText(player, "kibu-nbs.music.none_playing").formatted(RED);
        } else {
            msg = translations.translateText(player, "kibu-nbs.music.stopped.all").formatted(GREEN);
        }

        player.sendMessage(msg);

        return empty ? 0 : 1;
    }

    private int stopAll(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");

        ServerCommandSource source = ctx.getSource();
        KibuNbsApi api = KibuNbsApi.getInstance(source.getServer());

        int stopped = 0;

        for (ServerPlayerEntity listener : listeners) {
            for (SongHandle handle : api.getPlayingSongs(listener)) {
                stopped++;

                handle.remove(listener);
            }
        }

        RootText msg;

        boolean empty = stopped == 0;
        if (empty) {
            msg = translations.translateText(source, "kibu-nbs.music.none_playing").formatted(RED);
        } else {
            msg = translations.translateText(source, "kibu-nbs.music.stopped.all").formatted(GREEN);
        }

        source.sendMessage(msg);

        return empty ? 0 : 1;
    }

    private int stopSong(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        ServerCommandSource source = ctx.getSource();
        KibuNbsApi api = KibuNbsApi.getInstance(source.getServer());

        int stopped = 0;

        for (ServerPlayerEntity listener : listeners) {
            var optHandle = api.getPlayingSong(listener, id);

            if (optHandle.isEmpty()) continue;

            stopped++;
            optHandle.get().remove(listener);
        }

        if (stopped == 0) {
            var msg = translations.translateText(source, "kibu-nbs.music.not_playing", styled(id, YELLOW))
                    .formatted(RED);

            source.sendMessage(msg);
            return 0;
        }

        var msg = translations.translateText(source, "kibu-nbs.music.stopped", styled(id, YELLOW))
                .formatted(GREEN);

        source.sendMessage(msg);

        return 1;
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

    private CompletableFuture<Suggestions> commonPlayingSongIds(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");

        KibuNbsApi api = KibuNbsApi.getInstance(ctx.getSource().getServer());

        api.getPlayingSongs().stream()
                .filter(handle -> listeners.stream().allMatch(handle::isListener))
                .map(SongHandle::getSongId)
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

    private boolean isVanillaPlayer(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            return false;
        }

        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());

        return !instance.hasModInstalled(player);
    }
}
