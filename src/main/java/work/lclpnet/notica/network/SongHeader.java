package work.lclpnet.notica.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import work.lclpnet.notica.api.Index;
import work.lclpnet.notica.api.data.*;

public record SongHeader(Info info, LoopConfig loopConfig, Index<? extends LayerInfo> layerInfo, Instruments instruments) {

    public static final StreamCodec<FriendlyByteBuf, SongHeader> PACKET_CODEC = StreamCodec.composite(
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

        public static final StreamCodec<FriendlyByteBuf, Info> PACKET_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Info::durationTicks,
                NoticaPacketCodecs.SONG_TEMPO, Info::tempo,
                ByteBufCodecs.BOOL, Info::stereo,
                ByteBufCodecs.BYTE, Info::signature,
                Info::new);

        public Info(Song song) {
            this(song.durationTicks(), song.tempo(), song.stereo(), song.signature());
        }
    }
}
