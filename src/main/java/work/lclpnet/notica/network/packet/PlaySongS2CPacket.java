package work.lclpnet.notica.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.network.NoticaPacketCodecs;
import work.lclpnet.notica.network.SongHeader;
import work.lclpnet.notica.network.SongPlayOptions;

public record PlaySongS2CPacket(
        SongPlayOptions playOptions,
        SongHeader header,
        SongSlice slice,
        boolean last,
        byte[] checksum
) implements CustomPacketPayload {

    public static final Type<PlaySongS2CPacket> ID = new Type<>(NoticaInit.identifier("play"));

    public static final StreamCodec<FriendlyByteBuf, PlaySongS2CPacket> CODEC = StreamCodec.composite(
            SongPlayOptions.PACKET_CODEC, PlaySongS2CPacket::playOptions,
            SongHeader.PACKET_CODEC, PlaySongS2CPacket::header,
            NoticaPacketCodecs.SONG_SLICE_PACKET_CODEC, PlaySongS2CPacket::slice,
            ByteBufCodecs.BOOL, PlaySongS2CPacket::last,
            ByteBufCodecs.BYTE_ARRAY, PlaySongS2CPacket::checksum,
            PlaySongS2CPacket::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
