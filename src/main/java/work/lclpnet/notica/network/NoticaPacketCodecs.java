package work.lclpnet.notica.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.notica.api.*;
import work.lclpnet.notica.api.data.*;
import work.lclpnet.notica.impl.FixedIndex;
import work.lclpnet.notica.impl.data.ImmutableCustomInstrument;
import work.lclpnet.notica.impl.data.ImmutableInstruments;
import work.lclpnet.notica.impl.data.ImmutableLoopConfig;
import work.lclpnet.notica.impl.data.ImmutableSongTempo;
import work.lclpnet.notica.util.PlayerConfigEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class NoticaPacketCodecs {

    private NoticaPacketCodecs() {}

    public static final StreamCodec<FriendlyByteBuf, PlayerConfig> PLAYER_CONFIG_PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, PlayerConfig::getVolume,
            volume -> {
                PlayerConfigEntry config = new PlayerConfigEntry();
                config.setVolume(volume);
                return config;
            });

    public static final StreamCodec<FriendlyByteBuf, LoopConfig> LOOP_CONFIG_PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LoopConfig::enabled,
            ByteBufCodecs.INT, LoopConfig::loopCount,
            ByteBufCodecs.INT, LoopConfig::loopStartTick,
            ImmutableLoopConfig::new);

    public static final StreamCodec<FriendlyByteBuf, Index<? extends LayerInfo>> LAYER_INFO_PACKET_CODEC = StreamCodec.ofMember((layerInfo, buf) -> {
        buf.writeInt(layerInfo.size());

        for (var entry : layerInfo.iterateOrdered()) {
            buf.writeInt(entry.index());

            LayerInfo layer = entry.value();

            buf.writeByte(layer.volume());
            buf.writeShort(layer.panning());
            buf.writeBoolean(layer.locked());
        }
    }, buf -> {
        record LayerProto(byte volume, short panning, boolean locked) implements LayerInfo {}

        int layerCount = buf.readInt();
        var layerInfo = new HashMap<Integer, LayerInfo>(layerCount);

        for (int i = 0; i < layerCount; i++) {
            int key = buf.readInt();

            byte volume = buf.readByte();
            short panning = buf.readShort();
            boolean locked = buf.readBoolean();

            layerInfo.put(key, new LayerProto(volume, panning, locked));
        }

        return new FixedIndex<>(layerInfo);
    });

    public static final StreamCodec<FriendlyByteBuf, Instruments> INSTRUMENTS_PACKET_CODEC = StreamCodec.ofMember((instruments, buf) -> {
        var custom = instruments.custom();
        buf.writeInt(custom.length);

        for (CustomInstrument instrument : custom) {
            buf.writeUtf(instrument.name());
            buf.writeUtf(instrument.soundFile());
            buf.writeByte(instrument.key());
        }

        buf.writeInt(instruments.customBegin());
    }, buf -> {
        int customCount = buf.readInt();
        var custom = new CustomInstrument[customCount];

        for (int i = 0; i < customCount; i++) {
            String name = buf.readUtf();
            String soundFile = buf.readUtf();
            byte key = buf.readByte();

            custom[i] = new ImmutableCustomInstrument(name, soundFile, key);
        }

        int begin = buf.readInt();

        return new ImmutableInstruments(custom, begin);
    });

    public static final StreamCodec<FriendlyByteBuf, SongSlice> SONG_SLICE_PACKET_CODEC = StreamCodec.of(
            SongSlicer::writeSlice, SongSlicer::readSlice);

    public static final StreamCodec<FriendlyByteBuf, SongTempo> SONG_TEMPO = StreamCodec.ofMember((tempo, buf) -> {
        List<TempoChange> sections = tempo.changes();
        buf.writeInt(sections.size());

        for (TempoChange change : sections) {
            buf.writeInt(change.timeTick());
            buf.writeFloat(change.ticksPerSecond());
        }
    }, buf -> {
        int count = buf.readInt();
        List<TempoChange> changes = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int timeTick = buf.readInt();
            float ticksPerSecond = buf.readFloat();

            changes.add(new TempoChange(timeTick, ticksPerSecond));
        }

        return new ImmutableSongTempo(changes);
    });

    public static final StreamCodec<FriendlyByteBuf, LoopOverride> LOOP_OVERRIDE = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.BOOL), LoopOverride::enabled,
            ByteBufCodecs.optional(ByteBufCodecs.INT), LoopOverride::loopCount,
            ByteBufCodecs.optional(ByteBufCodecs.INT), LoopOverride::loopStartTick,
            LoopOverride::new
    );

    public static final StreamCodec<FriendlyByteBuf, PlaybackOptions> PLAYBACK_OPTIONS = StreamCodec.composite(
            ByteBufCodecs.FLOAT, PlaybackOptions::volume,
            indexed(
                    ByIdMap.continuous(PlaybackVariant::ordinal, PlaybackVariant.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
                    PlaybackVariant::ordinal
            ), PlaybackOptions::playbackVariant,
            indexed(
                    ByIdMap.continuous(StereoMode::ordinal, StereoMode.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
                    StereoMode::ordinal
            ), PlaybackOptions::stereoMode,
            LOOP_OVERRIDE, PlaybackOptions::loopOverride,
            indexed(
                    ByIdMap.continuous(ChannelMode::ordinal, ChannelMode.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
                    ChannelMode::ordinal
            ), PlaybackOptions::channelMode,
            PlaybackOptions::new);

    public static final StreamCodec<FriendlyByteBuf, Speaker> SPEAKER = StreamCodec.composite(
            Vec3.STREAM_CODEC, Speaker::position,
            ResourceKey.streamCodec(Registries.DIMENSION), Speaker::dimension,
            ByteBufCodecs.DOUBLE, Speaker::radius,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Speaker::sourceEntityUuid,
            ByteBufCodecs.BOOL, Speaker::dopplerEffect,
            Speaker::new);

    private static <T> StreamCodec<FriendlyByteBuf, T> indexed(IntFunction<T> idx2Val, ToIntFunction<T> val2Idx) {
        return StreamCodec.ofMember((val, buf) -> {
            int i = val2Idx.applyAsInt(val);
            VarInt.write(buf, i);
        }, buf -> {
            int i = VarInt.read(buf);
            return idx2Val.apply(i);
        });
    }
}
