package work.lclpnet.notica.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import work.lclpnet.notica.api.PlaybackOptions;

public record SongPlayOptions(ResourceLocation songId, PlaybackOptions playbackOptions, int startTick) {

    public static final StreamCodec<FriendlyByteBuf, SongPlayOptions> PACKET_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SongPlayOptions::songId,
            NoticaPacketCodecs.PLAYBACK_OPTIONS, SongPlayOptions::playbackOptions,
            ByteBufCodecs.INT, SongPlayOptions::startTick,
            SongPlayOptions::new);
}
