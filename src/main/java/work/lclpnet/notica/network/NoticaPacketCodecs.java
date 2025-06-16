package work.lclpnet.notica.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.util.function.ValueLists;
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

    public static final PacketCodec<PacketByteBuf, PlayerConfig> PLAYER_CONFIG_PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, PlayerConfig::getVolume,
            volume -> {
                PlayerConfigEntry config = new PlayerConfigEntry();
                config.setVolume(volume);
                return config;
            });

    public static final PacketCodec<PacketByteBuf, LoopConfig> LOOP_CONFIG_PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, LoopConfig::enabled,
            PacketCodecs.INTEGER, LoopConfig::loopCount,
            PacketCodecs.INTEGER, LoopConfig::loopStartTick,
            ImmutableLoopConfig::new);

    public static final PacketCodec<PacketByteBuf, Index<? extends LayerInfo>> LAYER_INFO_PACKET_CODEC = PacketCodec.of((layerInfo, buf) -> {
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

    public static final PacketCodec<PacketByteBuf, Instruments> INSTRUMENTS_PACKET_CODEC = PacketCodec.of((instruments, buf) -> {
        var custom = instruments.custom();
        buf.writeInt(custom.length);

        for (CustomInstrument instrument : custom) {
            buf.writeString(instrument.name());
            buf.writeString(instrument.soundFile());
            buf.writeByte(instrument.key());
        }

        buf.writeInt(instruments.customBegin());
    }, buf -> {
        int customCount = buf.readInt();
        var custom = new CustomInstrument[customCount];

        for (int i = 0; i < customCount; i++) {
            String name = buf.readString();
            String soundFile = buf.readString();
            byte key = buf.readByte();

            custom[i] = new ImmutableCustomInstrument(name, soundFile, key);
        }

        int begin = buf.readInt();

        return new ImmutableInstruments(custom, begin);
    });

    public static final PacketCodec<PacketByteBuf, SongSlice> SONG_SLICE_PACKET_CODEC = PacketCodec.ofStatic(
            SongSlicer::writeSlice, SongSlicer::readSlice);

    public static final PacketCodec<PacketByteBuf, SongTempo> SONG_TEMPO = PacketCodec.of((tempo, buf) -> {
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

    public static final PacketCodec<PacketByteBuf, PlaybackOptions> PLAYBACK_OPTIONS = PacketCodec.tuple(
            PacketCodecs.FLOAT, PlaybackOptions::volume,
            indexed(
                    ValueLists.createIndexToValueFunction(PlaybackVariant::ordinal, PlaybackVariant.values(), ValueLists.OutOfBoundsHandling.ZERO),
                    PlaybackVariant::ordinal
            ), PlaybackOptions::variant,
            indexed(
                    ValueLists.createIndexToValueFunction(StereoMode::ordinal, StereoMode.values(), ValueLists.OutOfBoundsHandling.ZERO),
                    StereoMode::ordinal
            ), PlaybackOptions::stereoMode,
            PlaybackOptions::new);

    private static <T> PacketCodec<PacketByteBuf, T> indexed(IntFunction<T> idx2Val, ToIntFunction<T> val2Idx) {
        return PacketCodec.of((val, buf) -> {
            int i = val2Idx.applyAsInt(val);
            VarInts.write(buf, i);
        }, buf -> {
            int i = VarInts.read(buf);
            return idx2Val.apply(i);
        });
    }
}
