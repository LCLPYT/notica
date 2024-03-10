package work.lclpnet.kibu.nbs.network;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;
import work.lclpnet.kibu.nbs.network.packet.PlaySongS2CPacket;
import work.lclpnet.kibu.nbs.network.packet.RequestSongC2SPacket;
import work.lclpnet.kibu.nbs.network.packet.RespondSongS2CPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend;

public class KibuNbsNetworking {

    public static final int MAX_PACKET_BYTES = 0x800000;  // packet size limit imposed by mc
    private static final int RESET_MILLIS = 20_000, MAX_REQUESTS = 40;

    private final Logger logger;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();

    public KibuNbsNetworking(Logger logger) {
        this.logger = logger;
    }

    public void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestSongC2SPacket.TYPE, this::onRequestSong);
    }

    private void onRequestSong(RequestSongC2SPacket packet, ServerPlayerEntity player, PacketSender sender) {
        PlayerData data = getData(player);

        if (data.throttle()) {
            logger.warn("Player {} is sending too many requests", player.getNameForScoreboard());
            return;
        }

        Identifier songId = packet.getSongId();
        KibuNbsApiImpl instance = KibuNbsApiImpl.getInstance(player.getServer());
        Song song = instance.getSongResolver().resolve(songId);

        if (song == null) {
            logger.warn("Player {} requested unknown song {}", player.getNameForScoreboard(), songId);
            return;
        }

        int tickOffset = packet.getTickOffset();
        int layerOffset = packet.getLayerOffset();

        logger.debug("Player {} requested song slice {}, {} for song {}", player.getNameForScoreboard(), tickOffset, layerOffset, songId);

        int maxLayerIndex = song.layers().streamKeys().max().orElse(-1);

        // check if there even is more data left to send
        int ticks = song.durationTicks();
        if (tickOffset > ticks || (tickOffset == ticks && layerOffset >= maxLayerIndex)) {
            logger.debug("Cannot send more song data for song {}, end is reached", songId);
            return;
        }

        int maxBytes = MAX_PACKET_BYTES - 200;  // leave a little bit of padding for the packet meta-data
        SongSlice slice = SongSlicer.sliceAt(song, tickOffset, layerOffset, maxBytes);
        RespondSongS2CPacket responsePacket = new RespondSongS2CPacket(songId, slice);

        ServerPlayNetworking.send(player, responsePacket);
    }

    private PlayerData getData(ServerPlayerEntity player) {
        synchronized (this) {
            return playerData.computeIfAbsent(player.getUuid(), uuid -> new PlayerData());
        }
    }

    public void onQuit(ServerPlayerEntity player) {
        synchronized (this) {
            playerData.remove(player.getUuid());
        }
    }

    public static boolean canSendAll(ServerPlayerEntity player) {
        return canSend(player, PlaySongS2CPacket.TYPE);
    }

    private static class PlayerData {
        private long lastRequest = 0L;
        private int count = 0;

        public boolean throttle() {
            long before = lastRequest;
            lastRequest = System.currentTimeMillis();

            if (lastRequest - before >= RESET_MILLIS) {
                count = 1;
                return false;
            }

            return ++count >= MAX_REQUESTS;
        }
    }
}
