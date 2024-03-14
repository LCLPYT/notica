package work.lclpnet.kibu.nbs.network;

import net.minecraft.network.PacketByteBuf;
import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.data.*;
import work.lclpnet.kibu.nbs.impl.FixedIndex;
import work.lclpnet.kibu.nbs.impl.data.ImmutableCustomInstrument;
import work.lclpnet.kibu.nbs.impl.data.ImmutableInstruments;
import work.lclpnet.kibu.nbs.impl.data.ImmutableLoopConfig;

import java.util.HashMap;

public class SongHeader {

    private final int durationTicks;
    private final float ticksPerSecond;
    private final LoopConfig loopConfig;
    private final Index<? extends LayerInfo> layerInfo;
    private final Instruments instruments;
    private final boolean stereo;
    private final byte signature;

    public SongHeader(Song song) {
        this.durationTicks = song.durationTicks();
        this.ticksPerSecond = song.ticksPerSecond();
        this.loopConfig = song.loopConfig();
        this.layerInfo = song.layers();
        this.instruments = song.instruments();
        this.stereo = song.stereo();
        this.signature = song.signature();
    }

    public SongHeader(PacketByteBuf buf) {
        this.durationTicks = buf.readInt();
        this.ticksPerSecond = buf.readFloat();
        this.loopConfig = readLoopConfig(buf);
        this.layerInfo = readLayerInfo(buf);
        this.instruments = readInstruments(buf);
        this.stereo = buf.readBoolean();
        this.signature = buf.readByte();
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(durationTicks);
        buf.writeFloat(ticksPerSecond);
        writeLoopConfig(buf);
        writeLayerInfo(buf);
        writeInstruments(buf);
        buf.writeBoolean(stereo);
        buf.writeByte(signature);
    }

    private void writeLoopConfig(PacketByteBuf buf) {
        buf.writeBoolean(loopConfig.enabled());
        buf.writeInt(loopConfig.loopCount());
        buf.writeInt(loopConfig.loopStartTick());
    }

    private void writeLayerInfo(PacketByteBuf buf) {
        buf.writeInt(layerInfo.size());

        for (var entry : layerInfo.iterateOrdered()) {
            buf.writeInt(entry.index());

            LayerInfo layer = entry.value();

            buf.writeByte(layer.volume());
            buf.writeShort(layer.panning());
        }
    }

    private void writeInstruments(PacketByteBuf buf) {
        var custom = instruments.custom();
        buf.writeInt(custom.length);

        for (CustomInstrument instrument : custom) {
            buf.writeString(instrument.name());
            buf.writeString(instrument.soundFile());
            buf.writeByte(instrument.key());
        }

        buf.writeInt(instruments.customBegin());
    }

    public int durationTicks() {
        return durationTicks;
    }

    public float ticksPerSecond() {
        return ticksPerSecond;
    }

    public LoopConfig loopConfig() {
        return loopConfig;
    }

    public Index<? extends LayerInfo> layerInfo() {
        return layerInfo;
    }

    public Instruments instruments() {
        return instruments;
    }

    public boolean stereo() {
        return stereo;
    }

    public byte signature() {
        return signature;
    }

    private LoopConfig readLoopConfig(PacketByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int loopCount = buf.readInt();
        int startTick = buf.readInt();

        return new ImmutableLoopConfig(enabled, loopCount, startTick);
    }

    private static Index<LayerInfo> readLayerInfo(PacketByteBuf buf) {
        record LayerProto(byte volume, short panning) implements LayerInfo {}

        int layerCount = buf.readInt();
        var layerInfo = new HashMap<Integer, LayerInfo>(layerCount);

        for (int i = 0; i < layerCount; i++) {
            int key = buf.readInt();

            byte volume = buf.readByte();
            short panning = buf.readShort();

            layerInfo.put(key, new LayerProto(volume, panning));
        }

        return new FixedIndex<>(layerInfo);
    }

    private static Instruments readInstruments(PacketByteBuf buf) {
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
    }
}
