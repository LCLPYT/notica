package work.lclpnet.notica.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.notica.util.NoticaPermissions;
import work.lclpnet.notica.util.PlaylistManager;
import work.lclpnet.notica.util.SongUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.ChatFormatting.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PlaylistCommand {

    private final Path songDirectory;
    private final Translations translations;
    private final PlaylistManager playlistManager;
    private final Logger logger;

    public PlaylistCommand(Path songDirectory, Translations translations, PlaylistManager playlistManager, Logger logger) {
        this.songDirectory = songDirectory;
        this.translations = translations;
        this.playlistManager = playlistManager;
        this.logger = logger;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("playlist")
                .then(createCommand())
                .then(addCommand())
                .then(listCommand())
                .then(removeCommand())
                .then(shareCommand())
                .then(unshareCommand()));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return literal("create")
                .requires(NoticaPermissions.COMMAND_PLAYLIST_CREATE.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("title", StringArgumentType.string())
                        .executes(this::createPlaylist));
    }

    private LiteralArgumentBuilder<CommandSourceStack> addCommand() {
        return literal("add")
                .requires(NoticaPermissions.COMMAND_PLAYLIST_CREATE.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("playlist", StringArgumentType.string())
                        .suggests(this::suggestOwnPlaylists)
                        .then(argument("song", StringArgumentType.string())
                                .suggests(this::availableSongFiles)
                                .executes(this::addSong)));
    }

    /** {@code /playlist list} lists all playlists; {@code /playlist list <name>} shows songs. */
    private LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return literal("list")
                .requires(NoticaPermissions.COMMAND_PLAYLIST_CREATE.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .executes(this::listPlaylists)
                .then(argument("playlist", StringArgumentType.string())
                        .suggests(this::suggestAccessiblePlaylists)
                        .executes(this::showPlaylist));
    }

    private LiteralArgumentBuilder<CommandSourceStack> removeCommand() {
        return literal("remove")
                .requires(NoticaPermissions.COMMAND_PLAYLIST_CREATE.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("playlist", StringArgumentType.string())
                        .suggests(this::suggestOwnPlaylists)
                        .executes(this::deletePlaylist)
                        .then(argument("song", StringArgumentType.string())
                                .suggests(this::suggestSongsInPlaylist)
                                .executes(this::removeSong)));
    }

    /** {@code /playlist share <playlist> public} or {@code /playlist share <playlist> with <players>}. */
    private LiteralArgumentBuilder<CommandSourceStack> shareCommand() {
        return literal("share")
                .requires(NoticaPermissions.COMMAND_PLAYLIST_SHARE.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("playlist", StringArgumentType.string())
                        .suggests(this::suggestOwnPlaylists)
                        .then(literal("public")
                                .requires(NoticaPermissions.COMMAND_PLAYLIST_SHARE_PUBLIC.ofAtLeast(PermissionLevel.GAMEMASTERS))
                                .executes(this::sharePublic))
                        .then(literal("with")
                                .then(argument("players", EntityArgument.players())
                                        .executes(this::sharePlayers))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unshareCommand() {
        return literal("unshare")
                .requires(NoticaPermissions.COMMAND_PLAYLIST_SHARE.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("playlist", StringArgumentType.string())
                        .suggests(this::suggestOwnPlaylists)
                        .executes(this::unshareAll)
                        .then(argument("players", EntityArgument.players())
                                .executes(this::unshareFromPlayers)));
    }

    private int createPlaylist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String title = StringArgumentType.getString(ctx, "title");

        boolean created = playlistManager.createPlaylist(player.getUUID(), title);

        if (!created) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.create.exists", styled(title, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.create.success", styled(title, YELLOW)).formatted(GREEN));
        return 1;
    }

    private int addSong(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");
        String song = StringArgumentType.getString(ctx, "song");

        boolean added = playlistManager.addSong(player.getUUID(), playlistName, song);

        if (!added) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.add.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.add.success", styled(song, YELLOW), styled(playlistName, YELLOW)).formatted(GREEN));
        return 1;
    }

    private int listPlaylists(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Map<String, PlaylistManager.PlaylistEntry> owned = playlistManager.getOwnPlaylists(player.getUUID());
        Map<UUID, Map<String, PlaylistManager.PlaylistEntry>> shared = playlistManager.getSharedWithMe(player.getUUID());

        if (owned.isEmpty() && shared.isEmpty()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.list.empty").formatted(YELLOW));
            return 0;
        }

        if (!owned.isEmpty()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.list.header").formatted(GOLD));

            for (PlaylistManager.PlaylistEntry entry : owned.values()) {
                MutableComponent line = translations.translateText(ctx.getSource(),
                        "notica.playlist.list.entry",
                        styled(entry.getTitle(), YELLOW),
                        styled(entry.getSongs().size(), AQUA)).formatted(WHITE).copy();

                if (entry.isPublic()) {
                    line.append(Component.literal(" [public]").withStyle(GREEN));
                } else if (!entry.getSharedWith().isEmpty()) {
                    line.append(Component.literal(" [shared]").withStyle(AQUA));
                }

                ctx.getSource().sendSystemMessage(line);
            }
        }

        if (!shared.isEmpty()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.list.shared_header").formatted(GOLD));

            for (Map.Entry<UUID, Map<String, PlaylistManager.PlaylistEntry>> ownerEntry : shared.entrySet()) {
                String ownerName = resolvePlayerName(ctx, ownerEntry.getKey());

                for (PlaylistManager.PlaylistEntry entry : ownerEntry.getValue().values()) {
                    ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                            "notica.playlist.list.shared_entry",
                            styled(entry.getTitle(), YELLOW),
                            styled(ownerName, AQUA),
                            styled(entry.getSongs().size(), AQUA)).formatted(WHITE));
                }
            }
        }

        return 1;
    }

    private int showPlaylist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");

        Optional<PlaylistManager.PlaylistEntry> entryOpt =
                playlistManager.getAccessiblePlaylist(player.getUUID(), playlistName);

        if (entryOpt.isEmpty()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.remove.playlist.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        PlaylistManager.PlaylistEntry entry = entryOpt.get();

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.show.header", styled(entry.getTitle(), YELLOW)).formatted(GOLD));

        if (entry.getSongs().isEmpty()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.show.empty", styled(entry.getTitle(), YELLOW)).formatted(YELLOW));
            return 1;
        }

        for (String song : entry.getSongs()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.show.entry", styled(song, AQUA)).formatted(WHITE));
        }

        return 1;
    }

    private int deletePlaylist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");

        boolean deleted = playlistManager.deletePlaylist(player.getUUID(), playlistName);

        if (!deleted) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.remove.playlist.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.remove.playlist.success", styled(playlistName, YELLOW)).formatted(GREEN));
        return 1;
    }

    private int removeSong(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");
        String song = StringArgumentType.getString(ctx, "song");

        if (playlistManager.getPlaylist(player.getUUID(), playlistName).isEmpty()) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.remove.song.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        playlistManager.removeSong(player.getUUID(), playlistName, song);

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.remove.song.success", styled(song, YELLOW), styled(playlistName, YELLOW)).formatted(GREEN));
        return 1;
    }

    private int sharePlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");

        Set<UUID> targetUuids = targets.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
        boolean shared = playlistManager.shareWith(player.getUUID(), playlistName, targetUuids);

        if (!shared) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.share.players.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.share.players.success",
                styled(playlistName, YELLOW),
                styled(targets.size(), AQUA)).formatted(GREEN));
        return 1;
    }

    private int sharePublic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");

        boolean shared = playlistManager.makePublic(player.getUUID(), playlistName);

        if (!shared) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.share.public.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.share.public.success", styled(playlistName, YELLOW)).formatted(GREEN));
        return 1;
    }

    private int unshareAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");

        boolean unshared = playlistManager.unshareAll(player.getUUID(), playlistName);

        if (!unshared) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.unshare.all.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.unshare.all.success", styled(playlistName, YELLOW)).formatted(GREEN));
        return 1;
    }

    private int unshareFromPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playlistName = StringArgumentType.getString(ctx, "playlist");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");

        Set<UUID> targetUuids = targets.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
        boolean unshared = playlistManager.unshareFrom(player.getUUID(), playlistName, targetUuids);

        if (!unshared) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                    "notica.playlist.unshare.players.not_found", styled(playlistName, YELLOW)).formatted(RED));
            return 0;
        }

        ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(),
                "notica.playlist.unshare.players.success",
                styled(targets.size(), AQUA),
                styled(playlistName, YELLOW)).formatted(GREEN));
        return 1;
    }

    private CompletableFuture<Suggestions> suggestOwnPlaylists(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return builder.buildFuture();

        playlistManager.getOwnPlaylists(player.getUUID()).keySet().stream()
                .map(SongUtils::transformSongPath)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAccessiblePlaylists(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return builder.buildFuture();

        // own playlists first
        playlistManager.getOwnPlaylists(player.getUUID()).keySet().stream()
                .map(SongUtils::transformSongPath)
                .forEach(builder::suggest);

        // then shared playlists (deduplicate names already suggested)
        Set<String> alreadySuggested = playlistManager.getOwnPlaylists(player.getUUID()).keySet();
        playlistManager.getSharedWithMe(player.getUUID()).values().stream()
                .flatMap(m -> m.keySet().stream())
                .filter(k -> !alreadySuggested.contains(k))
                .map(SongUtils::transformSongPath)
                .distinct()
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSongsInPlaylist(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return builder.buildFuture();

        String playlistName = StringArgumentType.getString(ctx, "playlist");

        playlistManager.getPlaylist(player.getUUID(), playlistName)
                .ifPresent(entry -> entry.getSongs().stream()
                        .map(SongUtils::transformSongPath)
                        .forEach(builder::suggest));

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> availableSongFiles(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SongUtils.suggestSongFiles(songDirectory, builder, logger);
    }

    private String resolvePlayerName(CommandContext<CommandSourceStack> ctx, UUID uuid) {
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
        if (online != null) return online.getScoreboardName();
        return uuid.toString();
    }
}
