package work.lclpnet.notica.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.impl.NoticaImpl;
import work.lclpnet.notica.util.NoticaServerPackManager;
import work.lclpnet.notica.util.PlayerConfigContainer;
import work.lclpnet.notica.util.ServerSongLoader;
import work.lclpnet.notica.util.SongUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static me.lucko.fabric.api.permissions.v0.Permissions.require;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;
import static work.lclpnet.notica.NoticaInit.permission;

public class MusicCommand {

    private final Path songDirectory;
    private final Translations translations;
    private final NoticaServerPackManager serverPackManager;
    private final Logger logger;
    private final SimpleCommandExceptionType errorNoPermissionPlayOther, errorNoPermissionStopOther;

    public MusicCommand(Path songDirectory, Translations translations, NoticaServerPackManager serverPackManager, Logger logger) {
        this.songDirectory = songDirectory;
        this.translations = translations;
        this.serverPackManager = serverPackManager;
        this.logger = logger;

        errorNoPermissionPlayOther = new SimpleCommandExceptionType(Text.translatableWithFallback("notica.music.play.no_permission_other", "You don't have permission to play music to other players"));
        errorNoPermissionStopOther = new SimpleCommandExceptionType(Text.translatableWithFallback("notica.music.stop.no_permission_other", "You don't have permission to stop music for other players"));
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("music")
                .then(literal("play")
                        .requires(require(permission("command.music.play"), 2))
                        .then(argument("song", StringArgumentType.string())
                                .suggests(this::availableSongFiles)
                                .executes(this::playSongAutoSelf)
                                .then(argument("listeners", EntityArgumentType.players())
                                        .executes(this::playSongAuto)
                                        .then(argument("id", IdentifierArgumentType.identifier())
                                                .executes(this::playSongId)))))
                .then(literal("stop")
                        .requires(require(permission("command.music.stop"), 2))
                        .executes(this::stopAllSelf)
                        .then(argument("listeners", EntityArgumentType.players())
                                .executes(this::stopAll)
                                .then(argument("id", IdentifierArgumentType.identifier())
                                        .suggests(this::commonPlayingSongIds)
                                        .executes(this::stopSong))))
                .then(literal("set")
                        .then(literal("extended_range")
                                .requires(this::extendedRangePredicate)
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(this::changeExtendedRange)))
                        .then(literal("volume")
                                .then(argument("percent", FloatArgumentType.floatArg(0f, 100f))
                                        .executes(this::changeVolume))));
    }

    private int changeExtendedRange(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        if (enabled && !serverPackManager.hasServerPackInstalled(player)) {
            var msg = translations.translateText(player, "notica.music.server_pack_requesting").formatted(GRAY);
            player.sendMessage(msg);

            serverPackManager.sendServerPack(player);
            return 1;
        }

        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());

        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(enabled);

        String key = enabled ? "notica.music.extended_octaves.enabled" : "notica.music.extended_octaves.disabled";
        Formatting color = enabled ? GREEN : RED;

        var msg = translations.translateText(player, key).formatted(color);

        player.sendMessage(msg);

        return 2;
    }

    private int changeVolume(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        float percent = FloatArgumentType.getFloat(ctx, "percent");

        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());

        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setVolume(percent / 100);
        configs.saveConfig(player);
        instance.syncPlayerConfig(player);

        var msg = translations.translateText(player, "notica.music.volume.changed",
                styled("%.0f%%".formatted(percent), YELLOW)).formatted(GREEN);

        player.sendMessage(msg);

        return 1;
    }

    private int playSongAutoSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String songFile = StringArgumentType.getString(ctx, "song");

        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);

        // for auto, stop all other songs. Explicitly specify an id to prevent this.
        stopAllSongs(ctx.getSource(), List.of(player));

        return playSong(source, List.of(player), path, id);
    }

    private int playSongAuto(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");
        String songFile = StringArgumentType.getString(ctx, "song");

        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);

        // for auto, stop all other songs. Explicitly specify an id to prevent this.
        stopAllSongs(ctx.getSource(), listeners);

        return playSong(ctx.getSource(), listeners, path, id);
    }

    private int playSongId(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");
        String songFile = StringArgumentType.getString(ctx, "song");
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        Path path = songDirectory.resolve(songFile);

        return playSong(ctx.getSource(), listeners, path, id);
    }

    private boolean involvesOther(ServerCommandSource source, Collection<? extends ServerPlayerEntity> players) {
        ServerPlayerEntity executor = source.getPlayer();

        if (executor == null) {
            return !players.isEmpty();
        }

        return players.stream().anyMatch(player -> !executor.equals(player));
    }

    private int playSong(ServerCommandSource source, Collection<? extends ServerPlayerEntity> listeners, Path path, Identifier id) throws CommandSyntaxException {
        if (involvesOther(source, listeners) && !Permissions.check(source, permission("command.music.play.other"), 2)) {
            throw errorNoPermissionPlayOther.create();
        }

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
                msg = translations.translateText(source, "notica.music.play.not_found", styled(relativePath, YELLOW));
            } else {
                msg = translations.translateText(source, "notica.music.play.error");
            }

            msg.formatted(RED);

            source.sendMessage(msg);

            logger.error("Failed to load song file", error);
            return null;
        }).thenAccept(song -> {
            if (song == null) return;

            var msg = translations.translateText(source, "notica.music.play", styled(relativePath, YELLOW))
                    .formatted(GREEN);

            source.sendMessage(msg);

            Notica api = Notica.getInstance(source.getServer());
            api.playSong(song, 1f, listeners);
        });

        return 1;
    }

    private int stopAllSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int stopped = stopAllSongs(ctx.getSource(), List.of(player));

        RootText msg;

        if (stopped == 0) {
            msg = translations.translateText(player, "notica.music.none_playing").formatted(RED);
        } else {
            msg = translations.translateText(player, "notica.music.stopped.all").formatted(GREEN);
        }

        player.sendMessage(msg);

        return stopped;
    }

    private int stopAll(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");

        ServerCommandSource source = ctx.getSource();

        if (involvesOther(source, listeners) && !Permissions.check(source, permission("command.music.stop.other"), 2)) {
            throw errorNoPermissionStopOther.create();
        }

        int stopped = stopAllSongs(source, listeners);

        RootText msg;

        boolean empty = stopped == 0;
        if (empty) {
            msg = translations.translateText(source, "notica.music.none_playing").formatted(RED);
        } else {
            msg = translations.translateText(source, "notica.music.stopped.all").formatted(GREEN);
        }

        source.sendMessage(msg);

        return empty ? 0 : 1;
    }

    private int stopAllSongs(ServerCommandSource source, Collection<ServerPlayerEntity> listeners) {
        Notica api = Notica.getInstance(source.getServer());

        int stopped = 0;

        for (ServerPlayerEntity listener : listeners) {
            for (SongHandle handle : api.getPlayingSongs(listener)) {
                stopped++;

                handle.remove(listener);
            }
        }

        return stopped;
    }

    private int stopSong(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var listeners = EntityArgumentType.getPlayers(ctx, "listeners");
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        ServerCommandSource source = ctx.getSource();

        if (involvesOther(source, listeners) && !Permissions.check(source, permission("command.music.stop.other"), 2)) {
            throw errorNoPermissionStopOther.create();
        }

        Notica api = Notica.getInstance(source.getServer());

        int stopped = 0;

        for (ServerPlayerEntity listener : listeners) {
            var optHandle = api.getPlayingSong(listener, id);

            if (optHandle.isEmpty()) continue;

            stopped++;
            optHandle.get().remove(listener);
        }

        if (stopped == 0) {
            var msg = translations.translateText(source, "notica.music.not_playing", styled(id, YELLOW))
                    .formatted(RED);

            source.sendMessage(msg);
            return 0;
        }

        var msg = translations.translateText(source, "notica.music.stopped", styled(id, YELLOW))
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

        Notica api = Notica.getInstance(ctx.getSource().getServer());

        api.getPlayingSongs().stream()
                .filter(handle -> listeners.stream().allMatch(handle::isListener))
                .map(SongHandle::getSongId)
                .map(Identifier::toString)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private static String transformString(String s) {
        s = s.replace('\\', '/');

        boolean needsQuoting = false;

        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);

            if (!StringReader.isAllowedInUnquotedString(c)) {
                needsQuoting = true;
                break;
            }
        }

        if (needsQuoting) {
            s = '"' + s + '"';
        }

        return s;
    }

    private boolean extendedRangePredicate(ServerCommandSource source) {
        if (!serverPackManager.isEnabled()) return false;

        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            return false;
        }

        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());

        return !instance.hasModInstalled(player);
    }
}
