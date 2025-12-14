package work.lclpnet.notica.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import work.lclpnet.notica.NoticaInit;

public record SongSeekS2CPacket(ResourceLocation songId, int ticks, boolean absolute) implements CustomPacketPayload {

    public static final Type<SongSeekS2CPacket> ID = new Type<>(NoticaInit.identifier("seek"));

    public static final StreamCodec<FriendlyByteBuf, SongSeekS2CPacket> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SongSeekS2CPacket::songId,
            ByteBufCodecs.INT, SongSeekS2CPacket::ticks,
            ByteBufCodecs.BOOL, SongSeekS2CPacket::absolute,
            SongSeekS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
