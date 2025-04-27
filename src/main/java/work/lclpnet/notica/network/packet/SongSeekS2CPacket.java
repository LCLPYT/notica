package work.lclpnet.notica.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.NoticaInit;

public record SongSeekS2CPacket(Identifier songId, int ticks, boolean absolute) implements CustomPayload {

    public static final Id<SongSeekS2CPacket> ID = new Id<>(NoticaInit.identifier("seek"));

    public static final PacketCodec<PacketByteBuf, SongSeekS2CPacket> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, SongSeekS2CPacket::songId,
            PacketCodecs.INTEGER, SongSeekS2CPacket::ticks,
            PacketCodecs.BOOLEAN, SongSeekS2CPacket::absolute,
            SongSeekS2CPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
