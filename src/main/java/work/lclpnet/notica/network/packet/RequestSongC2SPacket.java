package work.lclpnet.notica.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import work.lclpnet.notica.NoticaInit;

public record RequestSongC2SPacket(Identifier songId, int tickOffset, int layerOffset) implements CustomPacketPayload {

    public static final Type<RequestSongC2SPacket> ID = new Type<>(NoticaInit.identifier("request"));

    public static final StreamCodec<FriendlyByteBuf, RequestSongC2SPacket> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, RequestSongC2SPacket::songId,
            ByteBufCodecs.VAR_INT, RequestSongC2SPacket::tickOffset,
            ByteBufCodecs.VAR_INT, RequestSongC2SPacket::layerOffset,
            RequestSongC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
