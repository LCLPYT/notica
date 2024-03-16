package work.lclpnet.notica.network;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.NoticaImpl;
import work.lclpnet.notica.mixin.ServerLoginNetworkHandlerAccessor;
import work.lclpnet.notica.network.packet.RequestSongC2SPacket;
import work.lclpnet.notica.network.packet.RespondSongS2CPacket;
import work.lclpnet.notica.network.packet.StopSongBidiPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoticaNetworking {

    public static final Identifier VERSION_LOGIN_CHANNEL = NoticaInit.identifier("version");
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PACKET_BYTES = 0x800000;  // packet size limit imposed by mc
    private static final int RESET_MILLIS = 20_000, MAX_REQUESTS = 40;
    private static NoticaNetworking instance = null;

    private final Logger logger;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();

    public NoticaNetworking(Logger logger) {
        this.logger = logger;
        instance = this;
    }

    public void register() {
        ServerLoginNetworking.registerGlobalReceiver(VERSION_LOGIN_CHANNEL, this::receiveLoginVersion);
        ServerPlayNetworking.registerGlobalReceiver(RequestSongC2SPacket.TYPE, this::onRequestSong);
        ServerPlayNetworking.registerGlobalReceiver(StopSongBidiPacket.TYPE, this::onSongStopped);

        ServerLoginConnectionEvents.QUERY_START.register(this::onLoginStart);

        ServerLoginConnectionEvents.DISCONNECT.register(this::onLoginDisconnect);

        PlayerConnectionHooks.QUIT.register(this::onQuit);
    }

    private void onLoginStart(ServerLoginNetworkHandler handler, MinecraftServer server, PacketSender sender, ServerLoginNetworking.LoginSynchronizer synchronizer) {
        // send notica protocol version query
        sender.sendPacket(VERSION_LOGIN_CHANNEL, PacketByteBufs.empty());
    }

    private void receiveLoginVersion(MinecraftServer server, ServerLoginNetworkHandler handler, boolean understood,
                                     PacketByteBuf buf, ServerLoginNetworking.LoginSynchronizer synchronizer, PacketSender sender) {
        int version = 0;

        if (understood) {
            // the client understood the notica protocol version query
            version = buf.readVarInt();
        }

        logger.debug("Received protocol version {}", version);

        GameProfile profile = ((ServerLoginNetworkHandlerAccessor) handler).getProfile();

        if (profile == null) {
            logger.error("Game profile is not set, but should be initialized by now...");
            return;
        }

        UUID uuid = profile.getId();
        getData(uuid).version = version;
    }

    private void onLoginDisconnect(ServerLoginNetworkHandler handler, MinecraftServer server) {
        GameProfile profile = ((ServerLoginNetworkHandlerAccessor) handler).getProfile();
        if (profile == null) return;

        onQuit(profile.getId());
    }

    private void onRequestSong(RequestSongC2SPacket packet, ServerPlayerEntity player, PacketSender sender) {
        PlayerData data = getData(player);

        if (data.throttle()) {
            logger.warn("Player {} is sending too many requests", player.getNameForScoreboard());
            return;
        }

        Identifier songId = packet.getSongId();
        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());
        var optSong = instance.getSong(songId);

        if (optSong.isEmpty()) {
            logger.warn("Player {} requested unknown song {}", player.getNameForScoreboard(), songId);
            return;
        }

        Song song = optSong.get();

        int tickOffset = packet.getTickOffset();
        int layerOffset = packet.getLayerOffset();

        logger.debug("Player {} requested song slice {}, {} for song {}", player.getNameForScoreboard(), tickOffset, layerOffset, songId);

        // check if there even is more data left to send
        if (SongSlicer.isFinished(song, tickOffset, layerOffset)) {
            logger.debug("Cannot send more song data for song {}, end is reached", songId);
            return;
        }

        int maxBytes = MAX_PACKET_BYTES - 200;  // leave a little bit of padding for the packet meta-data
        SongSlice slice = SongSlicer.sliceAt(song, tickOffset, layerOffset, maxBytes);
        boolean finished = SongSlicer.isFinished(song, slice);

        if (finished) {
            logger.debug("Song slice response reached the end (song {})", songId);
        }

        RespondSongS2CPacket responsePacket = new RespondSongS2CPacket(songId, slice, finished);

        ServerPlayNetworking.send(player, responsePacket);
    }

    private void onSongStopped(StopSongBidiPacket packet, ServerPlayerEntity player, PacketSender sender) {
        Identifier songId = packet.getSongId();

        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());

        instance.notifySongStopped(player, songId);
    }

    private PlayerData getData(ServerPlayerEntity player) {
        return getData(player.getUuid());
    }

    private PlayerData getData(UUID uuid) {
        synchronized (this) {
            return playerData.computeIfAbsent(uuid, _uuid -> new PlayerData());
        }
    }

    private void onQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        onQuit(uuid);
    }

    private void onQuit(UUID uuid) {
        synchronized (this) {
            playerData.remove(uuid);
        }
    }

    public boolean understandsProtocol(ServerPlayerEntity player) {
        return getData(player).understandsProtocol();
    }

    public static NoticaNetworking getInstance() {
        if (instance == null) throw new IllegalStateException("Notica networking is not yet initialized");
        return instance;
    }

    private static class PlayerData {
        private long lastRequest = 0L;
        private int count = 0;
        private int version = 0;

        public boolean throttle() {
            long before = lastRequest;
            lastRequest = System.currentTimeMillis();

            if (lastRequest - before >= RESET_MILLIS) {
                count = 1;
                return false;
            }

            return ++count >= MAX_REQUESTS;
        }

        public boolean understandsProtocol() {
            return version == PROTOCOL_VERSION;
        }
    }
}
