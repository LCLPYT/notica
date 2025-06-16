package work.lclpnet.notica.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.api.PlaybackOptions;

public record SongPlayOptions(Identifier songId, PlaybackOptions playbackOptions, int startTick) {

    public static final PacketCodec<PacketByteBuf, SongPlayOptions> PACKET_CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, SongPlayOptions::songId,
            NoticaPacketCodecs.PLAYBACK_OPTIONS, SongPlayOptions::playbackOptions,
            PacketCodecs.INTEGER, SongPlayOptions::startTick,
            SongPlayOptions::new);
}
