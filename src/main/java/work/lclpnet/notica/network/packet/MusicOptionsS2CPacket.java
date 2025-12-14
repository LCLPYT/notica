package work.lclpnet.notica.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.network.NoticaPacketCodecs;

public record MusicOptionsS2CPacket(PlayerConfig config) implements CustomPacketPayload {

    public static final Type<MusicOptionsS2CPacket> ID = new Type<>(NoticaInit.identifier("options"));

    public static StreamCodec<FriendlyByteBuf, MusicOptionsS2CPacket> CODEC = StreamCodec.composite(
            NoticaPacketCodecs.PLAYER_CONFIG_PACKET_CODEC, MusicOptionsS2CPacket::config,
            MusicOptionsS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
