package work.lclpnet.notica.cmd;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.api.data.SongMeta;
import work.lclpnet.notica.impl.NoticaImpl;
import work.lclpnet.notica.util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.signum;
import static java.lang.Math.abs;
import static java.util.stream.Collectors.toSet;
import static net.minecraft.ChatFormatting.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MusicCommand {

    public static final Pattern
            TIME_SEGMENT = Pattern.compile("((?:[+-]\\s*)?\\d+)\\s*(sec|min|ticks|[smt])"),
            TIME_PATTERN = Pattern.compile("^(?:%s)+$".formatted(TIME_SEGMENT.pattern()));

    public static final float DEFAULT_VOLUME = 0.5f;

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

        errorNoPermissionPlayOther = new SimpleCommandExceptionType(Component.translatableWithFallback("notica.music.play.no_permission_other", "You don't have permission to play music to other players"));
        errorNoPermissionStopOther = new SimpleCommandExceptionType(Component.translatableWithFallback("notica.music.stop.no_permission_other", "You don't have permission to stop music for other players"));
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(command());
    }

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("music")
                .then(playCommand())
                .then(stopCommand())
                .then(setCommand())
                .then(seekCommand());
    }

    private LiteralArgumentBuilder<CommandSourceStack> playCommand() {
        return literal("play")
                .requires(NoticaPermissions.COMMAND_MUSIC_PLAY.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("song", StringArgumentType.string())
                        .suggests(this::availableSongFiles)
                        .executes(this::playSongGlobal)
                        .then(playAtCommand())
                        .then(playForCommand())
                        // global non-speaker
                        .then(nonSpeakerOptions(ctx -> new PlayArgs(Set.of(), null))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> playAtCommand() {
        return literal("at")
                .requires(NoticaPermissions.COMMAND_MUSIC_PLAY_POSITIONAL.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(playAtPositionCommand())
                .then(playAtEntityCommand());
    }

    private LiteralArgumentBuilder<CommandSourceStack> playAtPositionCommand() {
        return literal("position")
                .then(argument("position", Vec3Argument.vec3())
                        .executes(ctx -> {
                            var pos = Vec3Argument.getVec3(ctx, "position");
                            return playSongAuto(ctx, new PlayArgs(Set.of(), Speaker.fixed(pos)));
                        })
                        .then(literal("for")
                                .then(argument("listeners", EntityArgument.players())
                                        .executes(ctx -> {
                                            var pos = Vec3Argument.getVec3(ctx, "position");
                                            var listeners = EntityArgument.getPlayers(ctx, "listeners");
                                            return playSongAuto(ctx, new PlayArgs(listeners, Speaker.fixed(pos)));
                                        })
                                        .then(speakerOptions(
                                                (ctx, doppler, radius) -> new PlayArgs(
                                                        EntityArgument.getPlayers(ctx, "listeners"),
                                                        Speaker.fixed(Vec3Argument.getVec3(ctx, "position"), radius)),
                                                false))))
                        .then(speakerOptions(
                                (ctx, doppler, radius) -> new PlayArgs(
                                        Set.of(),
                                        Speaker.fixed(Vec3Argument.getVec3(ctx, "position"), radius)),
                                false)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> playAtEntityCommand() {
        return literal("entity")
                .then(argument("source", EntityArgument.entity())
                        .executes(ctx -> {
                            var source = EntityArgument.getEntity(ctx, "source");
                            return playSongAuto(ctx, new PlayArgs(Set.of(), Speaker.ofEntity(source)));
                        })
                        .then(literal("for")
                                .then(argument("listeners", EntityArgument.players())
                                        .executes(ctx -> {
                                            var source = EntityArgument.getEntity(ctx, "source");
                                            var listeners = EntityArgument.getPlayers(ctx, "listeners");
                                            return playSongAuto(ctx, new PlayArgs(listeners, Speaker.ofEntity(source)));
                                        })
                                        .then(speakerOptions(
                                                (ctx, doppler, radius) -> new PlayArgs(
                                                        EntityArgument.getPlayers(ctx, "listeners"),
                                                        Speaker.ofEntity(EntityArgument.getEntity(ctx, "source"), radius, doppler)),
                                                true))))
                        .then(speakerOptions(
                                (ctx, doppler, radius) -> new PlayArgs(
                                        Set.of(),
                                        Speaker.ofEntity(EntityArgument.getEntity(ctx, "source"), radius, doppler)),
                                true)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> playForCommand() {
        return literal("for")
                .then(argument("listeners", EntityArgument.players())
                        .executes(ctx -> {
                            var listeners = EntityArgument.getPlayers(ctx, "listeners");
                            return playSongAuto(ctx, new PlayArgs(listeners, null));
                        })
                        .then(nonSpeakerOptions(ctx -> new PlayArgs(
                                EntityArgument.getPlayers(ctx, "listeners"), null))));
    }

    private record PlayArgs(
            @NotNull Collection<ServerPlayer> listeners,
            @Nullable Speaker speaker
    ) {
        Collection<ServerPlayer> affectedPlayers(ServerLevel level) {
            if (listeners.isEmpty()) {
                return PlayerLookup.all(level.getServer());
            }

            return listeners;
        }
    }

    private interface PlayArgsFactory {
        PlayArgs create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private interface SpeakerArgsFactory {
        PlayArgs create(CommandContext<CommandSourceStack> ctx, boolean doppler, double radius) throws CommandSyntaxException;
    }

    private RequiredArgumentBuilder<CommandSourceStack, Float> nonSpeakerOptions(PlayArgsFactory factory) {
        return argument("volume", FloatArgumentType.floatArg(0f, 1f))
                .executes(ctx -> playSongVolume(ctx, factory.create(ctx)))
                .then(literal("individual")
                        .executes(ctx -> playSongVariant(ctx, PlaybackVariant.INDIVIDUAL, factory.create(ctx)))
                        .then(argument("id", IdentifierArgument.id())
                                .executes(ctx -> playSongId(ctx, PlaybackVariant.INDIVIDUAL, StereoMode.SPATIAL, factory.create(ctx)))))
                .then(literal("streamed")
                        .executes(ctx -> playSongVariant(ctx, PlaybackVariant.STREAMED, factory.create(ctx)))
                        .then(literal("spatial")
                                .executes(ctx -> playSongStereo(ctx, StereoMode.SPATIAL, factory.create(ctx)))
                                .then(argument("id", IdentifierArgument.id())
                                        .executes(ctx -> playSongId(ctx, PlaybackVariant.STREAMED, StereoMode.SPATIAL, factory.create(ctx)))))
                        .then(literal("equal_power")
                                .executes(ctx -> playSongStereo(ctx, StereoMode.EQUAL_POWER, factory.create(ctx)))
                                .then(argument("id", IdentifierArgument.id())
                                        .executes(ctx -> playSongId(ctx, PlaybackVariant.STREAMED, StereoMode.EQUAL_POWER, factory.create(ctx))))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Float> speakerOptions(SpeakerArgsFactory factory, boolean entitySpeaker) {
        var builder = argument("volume", FloatArgumentType.floatArg(0f, 1f));

        addSpeakerTerminals(builder, factory, PlaybackVariant.STREAMED, ChannelMode.STEREO, StereoMode.SPATIAL, false, entitySpeaker);

        builder.then(speakerVariant("individual", PlaybackVariant.INDIVIDUAL, factory, entitySpeaker));
        builder.then(speakerVariant("streamed", PlaybackVariant.STREAMED, factory, entitySpeaker));

        return builder;
    }

    private LiteralArgumentBuilder<CommandSourceStack> speakerVariant(String name, PlaybackVariant variant, SpeakerArgsFactory factory, boolean entitySpeaker) {
        var branch = literal(name);

        addSpeakerTerminals(branch, factory, variant, ChannelMode.STEREO, StereoMode.SPATIAL, false, entitySpeaker);

        branch.then(addSpeakerTerminals(literal("mono"), factory, variant, ChannelMode.MONO, StereoMode.SPATIAL, false, entitySpeaker));
        branch.then(speakerStereoChannel(variant, factory, entitySpeaker));

        return branch;
    }

    private LiteralArgumentBuilder<CommandSourceStack> speakerStereoChannel(PlaybackVariant variant, SpeakerArgsFactory factory, boolean entitySpeaker) {
        var branch = literal("stereo");

        addSpeakerTerminals(branch, factory, variant, ChannelMode.STEREO, StereoMode.SPATIAL, false, entitySpeaker);

        if (variant == PlaybackVariant.STREAMED) {
            branch.then(speakerStereoMode("spatial", StereoMode.SPATIAL, variant, factory, entitySpeaker));
            branch.then(speakerStereoMode("equal_power", StereoMode.EQUAL_POWER, variant, factory, entitySpeaker));
        }

        return branch;
    }

    private LiteralArgumentBuilder<CommandSourceStack> speakerStereoMode(String name, StereoMode stereoMode, PlaybackVariant variant, SpeakerArgsFactory factory, boolean entitySpeaker) {
        var branch = literal(name);

        branch.executes(speakerLeaf(factory, variant, ChannelMode.STEREO, stereoMode, false, false, false));

        branch.then(addSpeakerTerminals(
                argument("radius", FloatArgumentType.floatArg(0f, 15)),
                factory, variant, ChannelMode.STEREO, stereoMode, true, entitySpeaker));

        return branch;
    }

    private <T extends ArgumentBuilder<CommandSourceStack, T>> T addSpeakerTerminals(
            T builder, SpeakerArgsFactory factory, PlaybackVariant variant,
            ChannelMode channelMode, StereoMode stereoMode, boolean hasRadius, boolean entitySpeaker
    ) {
        builder.executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, false, false));

        builder.then(argument("id", IdentifierArgument.id())
                .executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, false, true)));

        if (entitySpeaker && channelMode == ChannelMode.MONO && variant == PlaybackVariant.STREAMED) {
            builder.then(literal("doppler")
                    .executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, true, false))
                    .then(argument("id", IdentifierArgument.id())
                            .executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, true, true))));
        }

        return builder;
    }

    private Command<CommandSourceStack> speakerLeaf(
            SpeakerArgsFactory factory, PlaybackVariant variant,
            ChannelMode channelMode, StereoMode stereoMode, boolean hasRadius, boolean doppler, boolean hasExplicitId
    ) {
        return ctx -> {
            String songFile = StringArgumentType.getString(ctx, "song");
            float volume = FloatArgumentType.getFloat(ctx, "volume");
            double radius = hasRadius ? FloatArgumentType.getFloat(ctx, "radius") : 1.0;

            Path path = songDirectory.resolve(songFile);
            Identifier id = hasExplicitId ? IdentifierArgument.getId(ctx, "id") : SongUtils.createSongId(path);

            PlayArgs args = factory.create(ctx, doppler, radius);

            if (!hasExplicitId) {
                stopAllSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));
            }

            return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, variant, stereoMode, channelMode));
        };
    }

    private int playSongGlobal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String songFile = StringArgumentType.getString(ctx, "song");
        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);
        PlayArgs args = new PlayArgs(Set.of(), null);

        // TODO global songs
        stopAllSongs(source, args.affectedPlayers(source.getLevel()));

        return playSong(source, args, path, id, new PlaybackOptions(DEFAULT_VOLUME));
    }

    private int playSongAuto(CommandContext<CommandSourceStack> ctx, PlayArgs args) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String songFile = StringArgumentType.getString(ctx, "song");
        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);

        stopAllSongs(source, args.affectedPlayers(source.getLevel()));

        return playSong(source, args, path, id, new PlaybackOptions(DEFAULT_VOLUME));
    }

    private int playSongVolume(CommandContext<CommandSourceStack> ctx, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);

        stopAllSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume));
    }

    private int playSongVariant(CommandContext<CommandSourceStack> ctx, PlaybackVariant variant, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);

        stopAllSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, variant, StereoMode.SPATIAL));
    }

    private int playSongStereo(CommandContext<CommandSourceStack> ctx, StereoMode stereoMode, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Path path = songDirectory.resolve(songFile);
        Identifier id = SongUtils.createSongId(path);

        stopAllSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, PlaybackVariant.STREAMED, stereoMode));
    }

    private int playSongId(CommandContext<CommandSourceStack> ctx, PlaybackVariant variant, StereoMode stereoMode, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Identifier id = IdentifierArgument.getId(ctx, "id");
        Path path = songDirectory.resolve(songFile);

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, variant, stereoMode));
    }

    private int playSong(CommandSourceStack source, PlayArgs args, Path path, Identifier id, PlaybackOptions options) throws CommandSyntaxException {
        if (involvesOther(source, args.affectedPlayers(source.getLevel()))
                && !NoticaPermissions.COMMAND_MUSIC_PLAY_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionPlayOther.create();
        }

        logger.debug("Playing song {} as {} with arguments {} and {}", path.getFileName(), id, args, options);

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
            source.sendSystemMessage(msg);
            logger.error("Failed to load song file", error);
            return null;
        }).thenAccept(song -> {
            if (song == null) return;

            source.sendSystemMessage(getPlayingMessage(source, relativePath, song));

            Notica api = Notica.getInstance(source.getServer());

            Speaker speaker = args.speaker();

            if (speaker == null) {
                api.playSong(song, options, 0, args.listeners());
                return;
            }

            api.playSongWithSpeaker(song, options, 0, speaker, args.listeners());
        });

        return 1;
    }

    private Component getPlayingMessage(CommandSourceStack source, Path relativePath, CheckedSong checkedSong) {
        SongMeta meta = checkedSong.song().metaData();
        String name = meta.name().isBlank() ? relativePath.toString() : meta.name();

        var nameText = Component.literal(name).withStyle(YELLOW);

        if (!meta.description().isBlank()) {
            var hoverText = Component.literal(meta.description()).withStyle(GREEN);
            nameText.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hoverText)));
        }

        if (meta.author().isBlank() && meta.originalAuthor().isBlank()) {
            return translations.translateText(source, "notica.music.play", nameText).formatted(GREEN);
        }

        if (!meta.author().isBlank() && !meta.originalAuthor().isBlank()) {
            return translations.translateText(
                    source,
                    "notica.music.play_author_original",
                    nameText,
                    styled(meta.author(), AQUA),
                    translations.translateText(source, "notica.music.original_author", meta.originalAuthor()).formatted(GRAY)
            ).formatted(GREEN);
        }

        String author = meta.author().isBlank() ? meta.originalAuthor() : meta.author();
        return translations.translateText(source, "notica.music.play_author", nameText, styled(author, AQUA)).formatted(GREEN);
    }

    // ---- stop command ----

    private LiteralArgumentBuilder<CommandSourceStack> stopCommand() {
        return literal("stop")
                .requires(NoticaPermissions.COMMAND_MUSIC_STOP.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .executes(this::stopAllSelf)
                .then(argument("listeners", EntityArgument.players())
                        .executes(this::stopAll)
                        .then(argument("id", IdentifierArgument.id())
                                .suggests(this::commonPlayingSongIds)
                                .executes(this::stopSong)));
    }

    private int stopAllSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int stopped = stopAllSongs(ctx.getSource(), List.of(player));

        RootText msg;

        if (stopped == 0) {
            msg = translations.translateText(player, "notica.music.none_playing").formatted(RED);
        } else {
            msg = translations.translateText(player, "notica.music.stopped.all").formatted(GREEN);
        }

        player.sendSystemMessage(msg);

        return stopped;
    }

    private int stopAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var listeners = EntityArgument.getPlayers(ctx, "listeners");
        CommandSourceStack source = ctx.getSource();

        if (involvesOther(source, listeners) && !NoticaPermissions.COMMAND_MUSIC_STOP_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionStopOther.create();
        }

        int stopped = stopAllSongs(source, listeners);

        RootText msg;
        if (stopped == 0) {
            msg = translations.translateText(source, "notica.music.none_playing").formatted(RED);
        } else {
            msg = translations.translateText(source, "notica.music.stopped.all").formatted(GREEN);
        }

        source.sendSystemMessage(msg);

        return stopped == 0 ? 0 : 1;
    }

    private int stopAllSongs(CommandSourceStack source, Collection<ServerPlayer> listeners) {
        Notica api = Notica.getInstance(source.getServer());
        int stopped = 0;

        for (ServerPlayer listener : listeners) {
            for (SongHandle handle : api.getPlayingSongs(listener)) {
                stopped++;
                handle.remove(listener);
            }
        }

        return stopped;
    }

    private int stopSong(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var listeners = EntityArgument.getPlayers(ctx, "listeners");
        Identifier id = IdentifierArgument.getId(ctx, "id");
        CommandSourceStack source = ctx.getSource();

        if (involvesOther(source, listeners) && !NoticaPermissions.COMMAND_MUSIC_STOP_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionStopOther.create();
        }

        Notica api = Notica.getInstance(source.getServer());
        int stopped = 0;

        for (ServerPlayer listener : listeners) {
            var optHandle = api.getPlayingSong(listener, id);
            if (optHandle.isEmpty()) continue;
            stopped++;
            optHandle.get().remove(listener);
        }

        if (stopped == 0) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.not_playing", styled(id, YELLOW)).formatted(RED));
            return 0;
        }

        source.sendSystemMessage(translations.translateText(source, "notica.music.stopped", styled(id, YELLOW)).formatted(GREEN));
        return 1;
    }

    private LiteralArgumentBuilder<CommandSourceStack> setCommand() {
        return literal("set")
                .then(literal("extended_range")
                        .requires(this::extendedRangePredicate)
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(this::changeExtendedRange)))
                .then(literal("volume")
                        .then(argument("percent", FloatArgumentType.floatArg(0f, 100f))
                                .executes(this::changeVolume)));
    }

    private int changeExtendedRange(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        if (enabled && !serverPackManager.hasServerPackInstalled(player)) {
            var msg = translations.translateText(player, "notica.music.server_pack_requesting").formatted(GRAY);
            player.sendSystemMessage(msg);
            serverPackManager.sendServerPack(player);
            return 1;
        }

        NoticaImpl instance = NoticaImpl.getInstance(player.level().getServer());
        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(enabled);

        String key = enabled ? "notica.music.extended_octaves.enabled" : "notica.music.extended_octaves.disabled";
        ChatFormatting color = enabled ? GREEN : RED;

        player.sendSystemMessage(translations.translateText(player, key).formatted(color));

        return 2;
    }

    private int changeVolume(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        float percent = FloatArgumentType.getFloat(ctx, "percent");

        NoticaImpl instance = NoticaImpl.getInstance(player.level().getServer());
        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setVolume(percent / 100);
        configs.saveConfig(player);
        instance.syncPlayerConfig(player);

        player.sendSystemMessage(translations.translateText(player, "notica.music.volume.changed",
                styled("%.0f%%".formatted(percent), YELLOW)).formatted(GREEN));

        return 1;
    }

    private LiteralArgumentBuilder<CommandSourceStack> seekCommand() {
        return literal("seek")
                .requires(NoticaPermissions.COMMAND_MUSIC_SEEK.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("time", StringArgumentType.string())
                        .suggests(this::suggestTimes)
                        .executes(this::seekAutoSelf)
                        .then(argument("listeners", EntityArgument.players())
                                .executes(this::seekAuto)
                                .then(argument("id", IdentifierArgument.id())
                                        .suggests(this::commonPlayingSongIds)
                                        .executes(this::seekId))));
    }

    private int seekAutoSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String time = StringArgumentType.getString(ctx, "time");
        CommandSourceStack source = ctx.getSource();

        TimeOffsets timeOffsets = parseOffsets(time, source);
        if (timeOffsets == null) return 0;

        Set<SongHandle> songHandles = Notica.getInstance(player.level().getServer()).getPlayingSongs(player);
        return seekAllWithOffsets(source, songHandles, timeOffsets);
    }

    private int seekAuto(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String time = StringArgumentType.getString(ctx, "time");
        var players = EntityArgument.getPlayers(ctx, "listeners");
        CommandSourceStack source = ctx.getSource();

        TimeOffsets timeOffsets = parseOffsets(time, source);
        if (timeOffsets == null) return 0;

        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> songHandles = players.stream()
                .flatMap(player -> api.getPlayingSongs(player).stream())
                .collect(toSet());

        return seekAllWithOffsets(source, songHandles, timeOffsets);
    }

    private int seekId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String time = StringArgumentType.getString(ctx, "time");
        var players = EntityArgument.getPlayers(ctx, "listeners");
        Identifier songId = IdentifierArgument.getId(ctx, "id");
        CommandSourceStack source = ctx.getSource();

        TimeOffsets timeOffsets = parseOffsets(time, source);
        if (timeOffsets == null) return 0;

        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> songHandles = players.stream()
                .flatMap(player -> api.getPlayingSong(player, songId).stream())
                .collect(toSet());

        return seekAllWithOffsets(source, songHandles, timeOffsets);
    }

    private int seekAllWithOffsets(CommandSourceStack source, Set<SongHandle> songHandles, TimeOffsets timeOffsets) {
        if (songHandles.isEmpty()) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.none_playing").formatted(RED));
            return 0;
        }

        for (SongHandle handle : songHandles) {
            seekWithOffsets(handle, timeOffsets);
        }

        ServerPlayer player = source.getPlayer();
        String language = player != null ? translations.getLanguage(player) : "en_us";

        FormatWrapper offsetsWrapped = styled(timeOffsets.translatedText(translations).translateTo(language), YELLOW);
        String translationKey = timeOffsets.absolute ? "notica.music.seek.absolute" : "notica.music.seek.relative";

        source.sendSystemMessage(translations.translateText(source, translationKey, offsetsWrapped).formatted(GREEN));

        return 1;
    }

    private boolean involvesOther(CommandSourceStack source, Collection<? extends ServerPlayer> players) {
        ServerPlayer executor = source.getPlayer();

        if (executor == null) {
            return !players.isEmpty();
        }

        return players.stream().anyMatch(player -> !executor.equals(player));
    }

    private @Nullable TimeOffsets parseOffsets(String time, CommandSourceStack source) {
        if (!TIME_PATTERN.matcher(time).matches()) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.seek.error_time", styled(time, YELLOW)).formatted(RED));
            return null;
        }

        Matcher matcher = TIME_SEGMENT.matcher(time);
        List<IntObjectPair<TimeUnit>> offsets = new ArrayList<>();
        boolean firstMatch = true;
        boolean absolute = true;
        boolean zero = false;

        while (matcher.find()) {
            String amountStr = matcher.group(1);
            String unitStr = matcher.group(2);

            if (firstMatch) {
                firstMatch = false;
                if (!amountStr.isEmpty() && (amountStr.charAt(0) == '+' || amountStr.charAt(0) == '-')) {
                    absolute = false;
                }
            }

            int amount;
            try {
                amount = parseInt(amountStr);
            } catch (NumberFormatException e) {
                source.sendSystemMessage(translations.translateText(source, "notica.music.seek.error_time", styled(amountStr, YELLOW)).formatted(RED));
                logger.error("Failed to parse as integer: {}", amountStr, e);
                continue;
            }

            TimeUnit unit = switch (unitStr) {
                case "sec", "s" -> TimeUnit.SECONDS;
                case "min", "m" -> TimeUnit.MINUTES;
                case "ticks", "t" -> TimeUnit.TICKS;
                default -> null;
            };

            if (unit == null) {
                logger.error("Unknown time unit: {}", unitStr);
                continue;
            }

            if (amount == 0) {
                zero = true;
                continue;
            }

            offsets.add(IntObjectPair.of(amount, unit));
        }

        if (offsets.isEmpty() && !(absolute && zero)) {
            return null;
        }

        return new TimeOffsets(offsets, absolute);
    }

    private void seekWithOffsets(SongHandle handle, TimeOffsets timeOffsets) {
        Song song = handle.getSong();
        int ticks = 0;

        for (IntObjectPair<TimeUnit> offset : timeOffsets.offsets()) {
            int amount = offset.keyInt();

            int offsetTicks = switch (offset.value()) {
                case TICKS -> abs(amount);
                case SECONDS -> song.tempo().durationTicks(ticks, amount);
                case MINUTES -> song.tempo().durationTicks(ticks, amount * 60);
            };

            ticks += signum(amount) * offsetTicks;
        }

        handle.seekTo(ticks, timeOffsets.absolute());
    }

    private CompletableFuture<Suggestions> availableSongFiles(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.walk(songDirectory, 8)) {
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

    private CompletableFuture<Suggestions> commonPlayingSongIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) throws CommandSyntaxException {
        var listeners = EntityArgument.getPlayers(ctx, "listeners");
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
            if (!StringReader.isAllowedInUnquotedString(s.charAt(i))) {
                needsQuoting = true;
                break;
            }
        }

        return needsQuoting ? '"' + s + '"' : s;
    }

    private boolean extendedRangePredicate(CommandSourceStack source) {
        if (!serverPackManager.isEnabled()) return false;

        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        return !NoticaImpl.hasModInstalled(player);
    }

    private CompletableFuture<Suggestions> suggestTimes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("+10s");
        builder.suggest("-10s");
        builder.suggest("15s");
        builder.suggest("1m5s");
        builder.suggest("50sec+3ticks");
        builder.suggest("1min-50ticks");
        return builder.buildFuture();
    }

    private record TimeOffsets(List<IntObjectPair<TimeUnit>> offsets, boolean absolute) {

        public TextTranslatable translatedText(Translations translations) {
            return lang -> {
                MutableComponent acc = Component.empty();
                boolean first = true;

                for (IntObjectPair<TimeUnit> timeUnitIntObjectPair : offsets) {
                    int num = timeUnitIntObjectPair.keyInt();
                    char sign = num >= 0 ? '+' : '-';

                    String translationKey = switch (timeUnitIntObjectPair.value()) {
                        case TICKS -> "notica.time.ticks";
                        case SECONDS -> "notica.time.seconds";
                        case MINUTES -> "notica.time.minutes";
                    };

                    String msg = first ? "" : " ";
                    msg += !absolute || !first ? sign + " " : "";
                    msg += translations.translateText(lang, translationKey, abs(num)).getString();

                    acc = acc.append(msg);
                    first = false;
                }

                return acc;
            };
        }
    }

    private enum TimeUnit { TICKS, SECONDS, MINUTES }
}
