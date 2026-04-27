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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
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
import java.util.*;
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
    private final SimpleCommandExceptionType errorNoPermissionPlayOther, errorNoPermissionStopOther, errorNoPermissionSeekOther;

    public MusicCommand(Path songDirectory, Translations translations, NoticaServerPackManager serverPackManager, Logger logger) {
        this.songDirectory = songDirectory;
        this.translations = translations;
        this.serverPackManager = serverPackManager;
        this.logger = logger;

        errorNoPermissionPlayOther = new SimpleCommandExceptionType(Component.translatableWithFallback("notica.music.play.no_permission_other", "You don't have permission to play music to other players"));
        errorNoPermissionStopOther = new SimpleCommandExceptionType(Component.translatableWithFallback("notica.music.stop.no_permission_other", "You don't have permission to stop music for other players"));
        errorNoPermissionSeekOther = new SimpleCommandExceptionType(Component.translatableWithFallback("notica.music.seek.no_permission_other", "You don't have permission to seek music for other players"));
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(command());
    }

    /** Builds the root {@code /music} command node with all subcommands attached. */
    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("music")
                .then(playCommand())
                .then(stopCommand())
                .then(addCommand())
                .then(setCommand())
                .then(seekCommand());
    }

    /** Builds {@code /music play}, which lets the executor play a song for themselves or others. */
    private LiteralArgumentBuilder<CommandSourceStack> playCommand() {
        return literal("play")
                .requires(NoticaPermissions.COMMAND_MUSIC_PLAY.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("song", StringArgumentType.string())
                        .suggests(this::availableSongFiles)
                        .executes(this::playSongSelf)
                        .then(playAtCommand())
                        .then(playForCommand())
                        .then(playGlobalCommand()));
    }

    /** Builds the {@code global} branch, which broadcasts a song to all online players without a speaker. */
    private LiteralArgumentBuilder<CommandSourceStack> playGlobalCommand() {
        return literal("global")
                .requires(NoticaPermissions.COMMAND_MUSIC_PLAY_OTHER.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .executes(ctx -> playSongAuto(ctx, new PlayArgs(Set.of(), null)))
                .then(nonSpeakerOptions(ctx -> new PlayArgs(Set.of(), null)));
    }

    /** Builds the {@code at} branch for positional playback via a fixed position or entity speaker. */
    private LiteralArgumentBuilder<CommandSourceStack> playAtCommand() {
        return literal("at")
                .requires(NoticaPermissions.COMMAND_MUSIC_PLAY_POSITIONAL.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(playAtPositionCommand())
                .then(playAtEntityCommand());
    }

    /** Builds the {@code position} branch, accepting a world position and optional listener selector. */
    private LiteralArgumentBuilder<CommandSourceStack> playAtPositionCommand() {
        return literal("position")
                .then(argument("position", Vec3Argument.vec3())
                        .executes(ctx -> {
                            var pos = Vec3Argument.getVec3(ctx, "position");
                            var level = ctx.getSource().getLevel();

                            return playSongAuto(ctx, new PlayArgs(Set.of(), Speaker.fixed(pos, level)));
                        })
                        .then(literal("for")
                                .then(argument("listeners", EntityArgument.players())
                                        .executes(ctx -> {
                                            var pos = Vec3Argument.getVec3(ctx, "position");
                                            var listeners = EntityArgument.getPlayers(ctx, "listeners");
                                            var level = ctx.getSource().getLevel();

                                            return playSongAuto(ctx, new PlayArgs(listeners, Speaker.fixed(pos, level)));
                                        })
                                        .then(speakerOptions(
                                                (ctx, doppler, radius, range) -> {
                                                    Vec3 pos = Vec3Argument.getVec3(ctx, "position");
                                                    Collection<ServerPlayer> listeners = EntityArgument.getPlayers(ctx, "listeners");
                                                    var level = ctx.getSource().getLevel();

                                                    return new PlayArgs(listeners, Speaker.fixed(pos, level, radius, range));
                                                },
                                                false))))
                        .then(speakerOptions(
                                (ctx, doppler, radius, range) -> {
                                    Vec3 pos = Vec3Argument.getVec3(ctx, "position");
                                    var level = ctx.getSource().getLevel();

                                    return new PlayArgs(
                                            Set.of(),
                                            Speaker.fixed(pos, level, radius, range));
                                },
                                false)));
    }

    /** Builds the {@code entity} branch, accepting an entity as the speaker source. */
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
                                                (ctx, doppler, radius, range) -> new PlayArgs(
                                                        EntityArgument.getPlayers(ctx, "listeners"),
                                                        Speaker.ofEntity(EntityArgument.getEntity(ctx, "source"), radius, range, doppler)),
                                                true))))
                        .then(speakerOptions(
                                (ctx, doppler, radius, range) -> new PlayArgs(
                                        Set.of(),
                                        Speaker.ofEntity(EntityArgument.getEntity(ctx, "source"), radius, range, doppler)),
                                true)));
    }

    /** Builds the {@code for} branch, which plays a song to the given player selector without a speaker. */
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

    /** Holds the resolved listener set and optional speaker for a play invocation. */
    private record PlayArgs(
            @NotNull Collection<ServerPlayer> listeners,
            @Nullable Speaker speaker
    ) {
        /** Returns the listeners to play to; falls back to all online players when the explicit list is empty. */
        Collection<ServerPlayer> affectedPlayers(ServerLevel level) {
            if (listeners.isEmpty()) {
                return PlayerLookup.all(level.getServer());
            }

            return listeners;
        }
    }

    /** Creates {@link PlayArgs} from command context without speaker arguments. */
    private interface PlayArgsFactory {
        PlayArgs create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    /** Creates {@link PlayArgs} from command context including resolved speaker parameters. */
    private interface SpeakerArgsFactory {
        PlayArgs create(CommandContext<CommandSourceStack> ctx, boolean doppler, double radius, float range) throws CommandSyntaxException;
    }

    /** Attaches the volume/variant/stereo subtree for non-speaker play paths. */
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

    /** Attaches the volume subtree for speaker paths, wiring up all variant and stereo sub-branches. */
    private RequiredArgumentBuilder<CommandSourceStack, Float> speakerOptions(SpeakerArgsFactory factory, boolean entitySpeaker) {
        var builder = argument("volume", FloatArgumentType.floatArg(0f, 1f));

        addSpeakerTerminals(builder, factory, PlaybackVariant.STREAMED, ChannelMode.STEREO, StereoMode.SPATIAL, false, false, entitySpeaker, false);

        builder.then(speakerVariant("individual", PlaybackVariant.INDIVIDUAL, factory, entitySpeaker));
        builder.then(speakerVariant("streamed", PlaybackVariant.STREAMED, factory, entitySpeaker));

        return builder;
    }

    /** Builds a named playback-variant branch ({@code individual} or {@code streamed}) within a speaker path. */
    private LiteralArgumentBuilder<CommandSourceStack> speakerVariant(String name, PlaybackVariant variant, SpeakerArgsFactory factory, boolean entitySpeaker) {
        var branch = literal(name);

        addSpeakerTerminals(branch, factory, variant, ChannelMode.STEREO, StereoMode.SPATIAL, false, false, entitySpeaker, false);

        branch.then(addSpeakerTerminals(literal("mono"), factory, variant, ChannelMode.MONO, StereoMode.SPATIAL, false, false, entitySpeaker, false));
        branch.then(speakerStereoChannel(variant, factory, entitySpeaker));

        return branch;
    }

    /** Builds the {@code stereo} literal that exposes optional stereo-mode sub-branches. */
    private LiteralArgumentBuilder<CommandSourceStack> speakerStereoChannel(PlaybackVariant variant, SpeakerArgsFactory factory, boolean entitySpeaker) {
        var branch = literal("stereo");

        addSpeakerTerminals(branch, factory, variant, ChannelMode.STEREO, StereoMode.SPATIAL, false, false, entitySpeaker, false);

        if (variant == PlaybackVariant.STREAMED) {
            branch.then(speakerStereoMode("spatial", StereoMode.SPATIAL, variant, factory, entitySpeaker));
            branch.then(speakerStereoMode("equal_power", StereoMode.EQUAL_POWER, variant, factory, entitySpeaker));
        }

        return branch;
    }

    /** Builds a named stereo-mode branch with optional range and radius sub-branches. */
    private LiteralArgumentBuilder<CommandSourceStack> speakerStereoMode(String name, StereoMode stereoMode, PlaybackVariant variant, SpeakerArgsFactory factory, boolean entitySpeaker) {
        var branch = literal(name);

        // no range, no radius
        branch.executes(speakerLeaf(factory, variant, ChannelMode.STEREO, stereoMode, false, false, false, false));

        // range → [terminals | radius → terminals]
        var rangeArg = addSpeakerTerminals(
                argument("range", FloatArgumentType.floatArg(16f, 64f)),
                factory, variant, ChannelMode.STEREO, stereoMode, false, true, entitySpeaker, false);

        rangeArg.then(addSpeakerTerminals(
                argument("radius", FloatArgumentType.floatArg(0f, 15)),
                factory, variant, ChannelMode.STEREO, stereoMode, true, true, entitySpeaker, true));

        branch.then(rangeArg);

        return branch;
    }

    /** Attaches execution leaves and optional range/doppler sub-arguments to {@code builder}.
     * {@code idAllowed} must be {@code false} whenever a float argument (range or radius) could still
     * appear as a sibling, to prevent the parser from ambiguously matching floats as identifiers. */
    private <T extends ArgumentBuilder<CommandSourceStack, T>> T addSpeakerTerminals(
            T builder, SpeakerArgsFactory factory, PlaybackVariant variant,
            ChannelMode channelMode, StereoMode stereoMode, boolean hasRadius, boolean hasRange,
            boolean entitySpeaker, boolean idAllowed
    ) {
        builder.executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, hasRange, false, false));

        if (idAllowed) {
            builder.then(argument("id", IdentifierArgument.id())
                    .executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, hasRange, false, true)));
        }

        if (entitySpeaker && channelMode == ChannelMode.MONO && variant == PlaybackVariant.STREAMED) {
            builder.then(literal("doppler")
                    .executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, hasRange, true, false))
                    .then(argument("id", IdentifierArgument.id())
                            .executes(speakerLeaf(factory, variant, channelMode, stereoMode, hasRadius, hasRange, true, true))));
        }

        if (!hasRange) {
            builder.then(addSpeakerTerminals(
                    argument("range", FloatArgumentType.floatArg(0f, 64f)),
                    factory, variant, channelMode, stereoMode, hasRadius, true, entitySpeaker, true));
        }

        return builder;
    }

    /** Returns the terminal {@link Command} that resolves all arguments and delegates to {@link #playSong}. */
    private Command<CommandSourceStack> speakerLeaf(
            SpeakerArgsFactory factory, PlaybackVariant variant,
            ChannelMode channelMode, StereoMode stereoMode, boolean hasRadius, boolean hasRange, boolean doppler, boolean hasExplicitId
    ) {
        return ctx -> {
            String songFile = StringArgumentType.getString(ctx, "song");
            float volume = FloatArgumentType.getFloat(ctx, "volume");
            double radius = hasRadius ? FloatArgumentType.getFloat(ctx, "radius") : 1.0;
            float range = hasRange ? FloatArgumentType.getFloat(ctx, "range") : 16.0f;

            Path path = songDirectory.resolve(songFile);
            Identifier id = hasExplicitId ? IdentifierArgument.getId(ctx, "id") : generateSongId(ctx, path);

            PlayArgs args = factory.create(ctx, doppler, radius, range);

            if (!hasExplicitId) {
                stopAllInvolvedSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));
            }

            return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, variant, stereoMode, channelMode));
        };
    }

    /** Plays a song to the executor only with default options. */
    private int playSongSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String songFile = StringArgumentType.getString(ctx, "song");
        Path path = songDirectory.resolve(songFile);
        Identifier id = generateSongId(ctx, path);
        PlayArgs args = new PlayArgs(List.of(player), null);

        stopAllInvolvedSongs(source, args.affectedPlayers(source.getLevel()));

        return playSong(source, args, path, id, new PlaybackOptions(DEFAULT_VOLUME));
    }

    /** Plays a song with default options using pre-resolved {@link PlayArgs}. */
    private int playSongAuto(CommandContext<CommandSourceStack> ctx, PlayArgs args) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String songFile = StringArgumentType.getString(ctx, "song");
        Path path = songDirectory.resolve(songFile);
        Identifier id = generateSongId(ctx, path);

        stopAllInvolvedSongs(source, args.affectedPlayers(source.getLevel()));

        return playSong(source, args, path, id, new PlaybackOptions(DEFAULT_VOLUME));
    }

    /** Plays a song with an explicit volume and default variant using pre-resolved {@link PlayArgs}. */
    private int playSongVolume(CommandContext<CommandSourceStack> ctx, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Path path = songDirectory.resolve(songFile);
        Identifier id = generateSongId(ctx, path);

        stopAllInvolvedSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume));
    }

    /** Derives a per-executor song ID from the song file path. */
    private @NonNull Identifier generateSongId(CommandContext<CommandSourceStack> ctx, Path path) {
        Identifier id = SongUtils.createSongId(path);

        return id.withSuffix("_" + ctx.getSource().getTextName().toLowerCase(Locale.ROOT));
    }

    /** Plays a song with an explicit volume and playback variant. */
    private int playSongVariant(CommandContext<CommandSourceStack> ctx, PlaybackVariant variant, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Path path = songDirectory.resolve(songFile);
        Identifier id = generateSongId(ctx, path);

        stopAllInvolvedSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, variant, StereoMode.SPATIAL));
    }

    /** Plays a pre-processed song with an explicit volume and stereo mixing mode. */
    private int playSongStereo(CommandContext<CommandSourceStack> ctx, StereoMode stereoMode, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Path path = songDirectory.resolve(songFile);
        Identifier id = generateSongId(ctx, path);

        stopAllInvolvedSongs(ctx.getSource(), args.affectedPlayers(ctx.getSource().getLevel()));

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, PlaybackVariant.STREAMED, stereoMode));
    }

    /** Plays a song with explicit volume, variant, stereo mode, and a caller-supplied song ID. */
    private int playSongId(CommandContext<CommandSourceStack> ctx, PlaybackVariant variant, StereoMode stereoMode, PlayArgs args) throws CommandSyntaxException {
        String songFile = StringArgumentType.getString(ctx, "song");
        float volume = FloatArgumentType.getFloat(ctx, "volume");
        Identifier id = IdentifierArgument.getId(ctx, "id");
        Path path = songDirectory.resolve(songFile);

        return playSong(ctx.getSource(), args, path, id, new PlaybackOptions(volume, variant, stereoMode));
    }

    /** Loads the song file asynchronously and starts playback once loaded. */
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

    /** Builds the "now playing" feedback message, including author and description where present. */
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

    /** Builds {@code /music stop} with sub-paths for stopping by ID, globally, or per player. */
    private LiteralArgumentBuilder<CommandSourceStack> stopCommand() {
        return literal("stop")
                .requires(NoticaPermissions.COMMAND_MUSIC_STOP.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .executes(this::stopAllOwn)
                .then(literal("id")
                        .then(argument("id", IdentifierArgument.id())
                                .suggests(this::allPlayingSongIds)
                                .executes(this::stopById)))
                .then(literal("all")
                        .requires(NoticaPermissions.COMMAND_MUSIC_STOP_OTHER.ofAtLeast(PermissionLevel.GAMEMASTERS))
                        .executes(this::stopAll))
                .then(literal("for")
                        .then(argument("listeners", EntityArgument.players())
                                .executes(this::stopAllForPlayers)
                                .then(argument("id", IdentifierArgument.id())
                                        .suggests(this::commonPlayingSongIds)
                                        .executes(this::removeByIdForPlayers))));
    }

    /** Builds {@code /music add}, which adds players to an already-playing song handle. */
    private LiteralArgumentBuilder<CommandSourceStack> addCommand() {
        return literal("add")
                .requires(NoticaPermissions.COMMAND_MUSIC_PLAY_OTHER.ofAtLeast(PermissionLevel.GAMEMASTERS))
                .then(argument("id", IdentifierArgument.id())
                        .suggests(this::allPlayingSongIds)
                        .then(argument("listeners", EntityArgument.players())
                                .executes(this::addPlayersToSong)));
    }

    /** Stops all song handles the executor is currently listening to. */
    private int stopAllOwn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> handles = api.getPlayingSongs(player);

        return stopAllHandles(source, handles);
    }

    /** Stops every active song handle on the server. */
    private int stopAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> handles = api.getPlayingSongs();

        return stopAllHandles(source, handles);
    }

    /** Calls {@link SongHandle#stop()} on each handle and sends the appropriate feedback message. */
    private int stopAllHandles(CommandSourceStack source, Set<SongHandle> handles) {
        if (handles.isEmpty()) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.none_playing").formatted(RED));
            return 0;
        }

        handles.forEach(SongHandle::stop);
        source.sendSystemMessage(translations.translateText(source, "notica.music.stopped.all").formatted(GREEN));
        return 1;
    }

    /** Stops the handle matching the given song ID, checking {@code STOP_OTHER} when the handle has other listeners. */
    private int stopById(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        CommandSourceStack source = ctx.getSource();
        Notica api = Notica.getInstance(source.getServer());
        SongHandle handle = api.getPlayingSong(id).orElse(null);

        if (handle == null) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.not_playing", styled(id, YELLOW)).formatted(RED));
            return 0;
        }

        Set<ServerPlayer> allListeners = handle.getListeners();

        if (involvesOther(source, allListeners) && !NoticaPermissions.COMMAND_MUSIC_STOP_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionStopOther.create();
        }

        handle.stop();
        source.sendSystemMessage(translations.translateText(source, "notica.music.stopped", styled(id, YELLOW)).formatted(GREEN));
        return 1;
    }

    /** Removes the given players from all their non-global song handles. */
    private int stopAllForPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var listeners = EntityArgument.getPlayers(ctx, "listeners");
        CommandSourceStack source = ctx.getSource();

        if (involvesOther(source, listeners) && !NoticaPermissions.COMMAND_MUSIC_STOP_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionStopOther.create();
        }

        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> handles = listeners.stream()
                .flatMap(p -> api.getPlayingSongs(p).stream())
                .collect(toSet());

        if (handles.isEmpty()) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.none_playing").formatted(RED));
            return 0;
        }

        int stopped = 0;

        for (SongHandle handle : handles) {
            if (handle.isGlobal()) {
                printGlobalStopError(source, handle.getSongId());
                continue;
            }

            for (ServerPlayer listener : listeners) {
                if (!handle.isListener(listener)) continue;
                handle.remove(listener);
                stopped++;
            }
        }

        if (stopped > 0) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.stopped.all").formatted(GREEN));
            return 1;
        }

        return 0;
    }

    /**
     * Stops all songs of the listeners. In order not to trigger this, pass an explicit id.
     */
    private void stopAllInvolvedSongs(CommandSourceStack source, Collection<ServerPlayer> listeners) {
        Notica api = Notica.getInstance(source.getServer());

        var handles = listeners.stream()
                .flatMap(player -> api.getPlayingSongs(player).stream())
                .collect(toSet());

        for (SongHandle handle : handles) {
            handle.stop();
        }
    }

    /** Removes the given players from the handle matching a specific song ID. */
    private int removeByIdForPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var listeners = EntityArgument.getPlayers(ctx, "listeners");
        Identifier id = IdentifierArgument.getId(ctx, "id");
        CommandSourceStack source = ctx.getSource();

        if (involvesOther(source, listeners) && !NoticaPermissions.COMMAND_MUSIC_STOP_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionStopOther.create();
        }

        Notica api = Notica.getInstance(source.getServer());
        SongHandle handle = api.getPlayingSong(id).orElse(null);

        if (handle == null) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.not_playing", styled(id, YELLOW)).formatted(RED));
            return 0;
        }

        if (handle.isGlobal()) {
            printGlobalStopError(source, id);
            return 0;
        }

        int stopped = 0;

        for (ServerPlayer listener : listeners) {
            if (!handle.isListener(listener)) continue;
            handle.remove(listener);
            stopped++;
        }

        if (stopped == 0) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.not_playing", styled(id, YELLOW)).formatted(RED));
            return 0;
        }

        source.sendSystemMessage(translations.translateText(source, "notica.music.stopped", styled(id, YELLOW)).formatted(GREEN));
        return 1;
    }

    /** Sends the "song is global" error and the {@code stop id} hint to the source. */
    private void printGlobalStopError(CommandSourceStack source, Identifier id) {
        source.sendSystemMessage(translations.translateText(source, "notica.music.stop.is_global", styled(id, YELLOW)).formatted(RED));
        source.sendSystemMessage(translations.translateText(source, "notica.music.stop.is_global.hint", styled(id, YELLOW)).formatted(GRAY));
    }

    /** Adds the given players to the handle matching a specific song ID. */
    private int addPlayersToSong(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        var listeners = EntityArgument.getPlayers(ctx, "listeners");
        CommandSourceStack source = ctx.getSource();
        Notica api = Notica.getInstance(source.getServer());
        SongHandle handle = api.getPlayingSong(id).orElse(null);

        if (handle == null) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.not_playing", styled(id, YELLOW)).formatted(RED));
            return 0;
        }

        int added = 0;

        for (ServerPlayer player : listeners) {
            if (handle.isListener(player)) continue;

            handle.add(player);

            added++;
        }

        if (added == 0) {
            source.sendSystemMessage(translations.translateText(source, "notica.music.add.already_listener", styled(id, YELLOW)).formatted(RED));
            return 0;
        }

        source.sendSystemMessage(translations.translateText(source, "notica.music.added", styled(added, YELLOW), styled(id, YELLOW)).formatted(GREEN));
        return added;
    }

    /** Builds {@code /music set} for client-side settings (extended range, volume). */
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

    /** Enables or disables extended octave range support for the executing player. */
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

    /** Sets the music playback volume for the executing player. */
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

    /** Builds {@code /music seek} for scrubbing to an absolute or relative playback position. */
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

    /** Seeks all songs the executor is listening to by the given time offset. */
    private int seekAutoSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String time = StringArgumentType.getString(ctx, "time");
        CommandSourceStack source = ctx.getSource();

        TimeOffsets timeOffsets = parseOffsets(time, source);
        if (timeOffsets == null) return 0;

        Set<SongHandle> songHandles = Notica.getInstance(player.level().getServer()).getPlayingSongs(player);
        return seekAllWithOffsets(source, songHandles, timeOffsets);
    }

    /** Seeks all songs the given players are listening to by the given time offset. */
    private int seekAuto(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String time = StringArgumentType.getString(ctx, "time");
        var players = EntityArgument.getPlayers(ctx, "listeners");
        CommandSourceStack source = ctx.getSource();

        if (involvesOther(source, players) && !NoticaPermissions.COMMAND_MUSIC_SEEK_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionSeekOther.create();
        }

        TimeOffsets timeOffsets = parseOffsets(time, source);
        if (timeOffsets == null) return 0;

        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> songHandles = players.stream()
                .flatMap(player -> api.getPlayingSongs(player).stream())
                .collect(toSet());

        return seekAllWithOffsets(source, songHandles, timeOffsets);
    }

    /** Seeks a specific song (by ID) for the given players. */
    private int seekId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String time = StringArgumentType.getString(ctx, "time");
        var players = EntityArgument.getPlayers(ctx, "listeners");
        Identifier songId = IdentifierArgument.getId(ctx, "id");
        CommandSourceStack source = ctx.getSource();

        if (involvesOther(source, players) && !NoticaPermissions.COMMAND_MUSIC_SEEK_OTHER.checkAtLeast(source, PermissionLevel.GAMEMASTERS)) {
            throw errorNoPermissionSeekOther.create();
        }

        TimeOffsets timeOffsets = parseOffsets(time, source);
        if (timeOffsets == null) return 0;

        Notica api = Notica.getInstance(source.getServer());
        Set<SongHandle> songHandles = players.stream()
                .flatMap(player -> api.getPlayingSong(player, songId).stream())
                .collect(toSet());

        return seekAllWithOffsets(source, songHandles, timeOffsets);
    }

    /** Applies time offsets to each handle and sends seek confirmation to the source. */
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

    /** Returns {@code true} if any player in the collection is not the command executor. */
    private boolean involvesOther(CommandSourceStack source, Collection<? extends ServerPlayer> players) {
        ServerPlayer executor = source.getPlayer();

        if (executor == null) {
            return !players.isEmpty();
        }

        return players.stream().anyMatch(player -> !executor.equals(player));
    }

    /** Parses a time string into a {@link TimeOffsets}; sends an error and returns {@code null} on failure. */
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

    /** Converts {@link TimeOffsets} to ticks and calls {@link SongHandle#seekTo}. */
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

    /** Suggests {@code .nbs} file paths relative to the song directory. */
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

    /** Suggests the IDs of all currently active song handles. */
    private CompletableFuture<Suggestions> allPlayingSongIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Notica api = Notica.getInstance(ctx.getSource().getServer());
        api.getPlayingSongs().stream()
                .map(SongHandle::getSongId)
                .map(Identifier::toString)
                .distinct()
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    /** Suggests song IDs that all given players are currently listening to. */
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

    /** Normalises a path string for command argument use, quoting it when it contains special characters. */
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

    /** Returns {@code true} only if the server pack is enabled and the executor is a non-modded player. */
    private boolean extendedRangePredicate(CommandSourceStack source) {
        if (!serverPackManager.isEnabled()) return false;

        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        return !NoticaImpl.hasModInstalled(player);
    }

    /** Provides example time strings as tab-completion suggestions. */
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

        /** Builds a human-readable representation of these offsets in the given language. */
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
