package work.lclpnet.notica.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import work.lclpnet.notica.NoticaInit;

public record StopSongBidiPacket(Identifier songId) implements CustomPacketPayload {

    public static final Type<StopSongBidiPacket> ID = new Type<>(NoticaInit.identifier("stop"));

    public static final StreamCodec<FriendlyByteBuf, StopSongBidiPacket> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, StopSongBidiPacket::songId,
            StopSongBidiPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
