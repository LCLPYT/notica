package work.lclpnet.notica.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.PlaybackOptions;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.network.NoticaPacketCodecs;
import work.lclpnet.notica.network.SongHeader;
import work.lclpnet.notica.network.SongPlayOptions;

public record PlaySongS2CPacket(SongPlayOptions playOptions, SongHeader header, SongSlice slice, boolean last, byte[] checksum) implements CustomPayload {

    public static final Id<PlaySongS2CPacket> ID = new Id<>(NoticaInit.identifier("play"));

    public static final PacketCodec<PacketByteBuf, PlaySongS2CPacket> CODEC = PacketCodec.tuple(
            SongPlayOptions.PACKET_CODEC, PlaySongS2CPacket::playOptions,
            SongHeader.PACKET_CODEC, PlaySongS2CPacket::header,
            NoticaPacketCodecs.SONG_SLICE_PACKET_CODEC, PlaySongS2CPacket::slice,
            PacketCodecs.BOOLEAN, PlaySongS2CPacket::last,
            PacketCodecs.BYTE_ARRAY, PlaySongS2CPacket::checksum,
            PlaySongS2CPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public Identifier getSongId() {
        return playOptions.songId();
    }

    public PlaybackOptions getPlaybackOptions() {
        return playOptions.playbackOptions();
    }

    public int getStartTick() {
        return playOptions.startTick();
    }
}
