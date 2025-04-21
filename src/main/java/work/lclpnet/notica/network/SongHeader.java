package work.lclpnet.notica.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import work.lclpnet.notica.api.Index;
import work.lclpnet.notica.api.data.*;

public record SongHeader(Info info, LoopConfig loopConfig, Index<? extends LayerInfo> layerInfo, Instruments instruments) {

    public static final PacketCodec<PacketByteBuf, SongHeader> PACKET_CODEC = PacketCodec.tuple(
            Info.PACKET_CODEC, SongHeader::info,
            NoticaPacketCodecs.LOOP_CONFIG_PACKET_CODEC, SongHeader::loopConfig,
            NoticaPacketCodecs.LAYER_INFO_PACKET_CODEC, SongHeader::layerInfo,
            NoticaPacketCodecs.INSTRUMENTS_PACKET_CODEC, SongHeader::instruments,
            SongHeader::new);

    public SongHeader(Song song) {
        this(new Info(song), song.loopConfig(), song.layers(), song.instruments());
    }

    public int durationTicks() {
        return info.durationTicks;
    }

    public SongTempo tempo() {
        return info.tempo;
    }

    public boolean stereo() {
        return info.stereo;
    }

    public byte signature() {
        return info.signature;
    }

    public record Info(int durationTicks, SongTempo tempo, boolean stereo, byte signature) {

        public static final PacketCodec<PacketByteBuf, Info> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, Info::durationTicks,
                NoticaPacketCodecs.SONG_TEMPO, Info::tempo,
                PacketCodecs.BOOLEAN, Info::stereo,
                PacketCodecs.BYTE, Info::signature,
                Info::new);

        public Info(Song song) {
            this(song.durationTicks(), song.tempo(), song.stereo(), song.signature());
        }
    }
}
