package work.lclpnet.notica.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.network.NoticaPacketCodecs;

public record RespondSongS2CPacket(ResourceLocation songId, SongSlice slice, boolean last) implements CustomPacketPayload {

    public static final Type<RespondSongS2CPacket> ID = new Type<>(NoticaInit.identifier("respond"));

    public static final StreamCodec<FriendlyByteBuf, RespondSongS2CPacket> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, RespondSongS2CPacket::songId,
            NoticaPacketCodecs.SONG_SLICE_PACKET_CODEC, RespondSongS2CPacket::slice,
            ByteBufCodecs.BOOL, RespondSongS2CPacket::last,
            RespondSongS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
