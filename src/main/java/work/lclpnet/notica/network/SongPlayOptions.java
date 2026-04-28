package work.lclpnet.notica.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import work.lclpnet.notica.api.PlaybackOptions;
import work.lclpnet.notica.api.Speaker;

import java.util.Optional;

public record SongPlayOptions(
        Identifier songId,
        PlaybackOptions playbackOptions,
        int startTick,
        Optional<Speaker> speaker
) {

    public static final StreamCodec<FriendlyByteBuf, SongPlayOptions> PACKET_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SongPlayOptions::songId,
            NoticaPacketCodecs.PLAYBACK_OPTIONS, SongPlayOptions::playbackOptions,
            ByteBufCodecs.INT, SongPlayOptions::startTick,
            ByteBufCodecs.optional(NoticaPacketCodecs.SPEAKER), SongPlayOptions::speaker,
            SongPlayOptions::new);
}
