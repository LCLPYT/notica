package work.lclpnet.notica.network;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.networking.protocol.Protocol;
import work.lclpnet.kibu.networking.protocol.ServerProtocolHandler;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.NoticaImpl;
import work.lclpnet.notica.mixin.ServerLoginPacketListenerImplAccessor;
import work.lclpnet.notica.network.packet.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoticaNetworking {

    public static final Protocol PROTOCOL = new Protocol(NoticaInit.identifier("version"), 5);
    public static final int MAX_PACKET_BYTES = 0x800000;  // packet size limit imposed by mc
    private static final int RESET_MILLIS = 15_000, MAX_REQUESTS = 40;
    private static NoticaNetworking instance = null;

    private final Logger logger;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private @Nullable ServerProtocolHandler protocolHandler = null;

    public NoticaNetworking(Logger logger) {
        this.logger = logger;
        instance = this;
    }

    public void register() {
        protocolHandler = new ServerProtocolHandler(PROTOCOL, logger);
        protocolHandler.register();

        var playS2C = PayloadTypeRegistry.playS2C();
        playS2C.register(MusicOptionsS2CPacket.ID, MusicOptionsS2CPacket.CODEC);
        playS2C.register(PlaySongS2CPacket.ID, PlaySongS2CPacket.CODEC);
        playS2C.register(RespondSongS2CPacket.ID, RespondSongS2CPacket.CODEC);
        playS2C.register(StopSongBidiPacket.ID, StopSongBidiPacket.CODEC);
        playS2C.register(SongSeekS2CPacket.ID, SongSeekS2CPacket.CODEC);

        var playC2S = PayloadTypeRegistry.playC2S();
        playC2S.register(RequestSongC2SPacket.ID, RequestSongC2SPacket.CODEC);
        playC2S.register(StopSongBidiPacket.ID, StopSongBidiPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestSongC2SPacket.ID, this::onRequestSong);
        ServerPlayNetworking.registerGlobalReceiver(StopSongBidiPacket.ID, this::onSongStopped);

        ServerLoginConnectionEvents.DISCONNECT.register(this::onLoginDisconnect);
        PlayerConnectionHooks.QUIT.register(this::onQuit);
    }

    private void onLoginDisconnect(ServerLoginPacketListenerImpl handler, MinecraftServer server) {
        GameProfile profile = ((ServerLoginPacketListenerImplAccessor) handler).getAuthenticatedProfile();
        if (profile == null) return;

        onQuit(profile.id());
    }

    private void onRequestSong(RequestSongC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        PlayerData data = getData(player);

        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && data.throttle()) {
            logger.warn("Player {} is sending too many requests", player.getScoreboardName());
            return;
        }

        Identifier songId = payload.songId();
        NoticaImpl instance = NoticaImpl.getInstance(player.level().getServer());
        var optSong = instance.getSong(songId);

        if (optSong.isEmpty()) {
            logger.warn("Player {} requested unknown song {}", player.getScoreboardName(), songId);
            return;
        }

        Song song = optSong.get();

        int tickOffset = payload.tickOffset();
        int layerOffset = payload.layerOffset();

        logger.debug("Player {} requested song slice {}, {} for song {}", player.getScoreboardName(), tickOffset, layerOffset, songId);

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

    private void onSongStopped(StopSongBidiPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        Identifier songId = payload.songId();

        NoticaImpl instance = NoticaImpl.getInstance(player.level().getServer());

        instance.notifySongStopped(player, songId);
    }

    private PlayerData getData(ServerPlayer player) {
        return getData(player.getUUID());
    }

    private PlayerData getData(UUID uuid) {
        synchronized (this) {
            return playerData.computeIfAbsent(uuid, _uuid -> new PlayerData());
        }
    }

    private void onQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        onQuit(uuid);
    }

    private void onQuit(UUID uuid) {
        synchronized (this) {
            playerData.remove(uuid);
        }
    }

    public boolean understandsProtocol(ServerPlayer player) {
        return protocolHandler != null && protocolHandler.understands(player);
    }

    public static NoticaNetworking getInstance() {
        if (instance == null) throw new IllegalStateException("Notica networking is not yet initialized");
        return instance;
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
