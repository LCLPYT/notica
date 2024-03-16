package work.lclpnet.notica.networking;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.impl.ClientMusicBackend;
import work.lclpnet.notica.impl.ClientSongRepository;
import work.lclpnet.notica.impl.PendingSong;
import work.lclpnet.notica.network.NoticaNetworking;
import work.lclpnet.notica.network.packet.*;
import work.lclpnet.notica.util.ByteHelper;
import work.lclpnet.notica.util.PlayerConfigEntry;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NoticaClientNetworking {

    private final ClientSongRepository songRepository;
    private final ClientMusicBackend controller;
    private final PlayerConfigEntry playerConfig;
    private final Logger logger;

    public NoticaClientNetworking(ClientSongRepository songRepository, ClientMusicBackend controller,
                                  PlayerConfigEntry playerConfig, Logger logger) {
        this.songRepository = songRepository;
        this.controller = controller;
        this.playerConfig = playerConfig;
        this.logger = logger;
    }

    public void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlaySongS2CPacket.TYPE, this::onPlaySong);
        ClientPlayNetworking.registerGlobalReceiver(RespondSongS2CPacket.TYPE, this::onRespondSong);
        ClientPlayNetworking.registerGlobalReceiver(StopSongBidiPacket.TYPE, this::onStopSong);
        ClientPlayNetworking.registerGlobalReceiver(MusicOptionsS2CPacket.TYPE, this::onMusicOptionsSync);

        ClientLoginNetworking.registerGlobalReceiver(NoticaNetworking.VERSION_LOGIN_CHANNEL, this::onQueryVersion);
    }

    private CompletableFuture<PacketByteBuf> onQueryVersion(MinecraftClient client, ClientLoginNetworkHandler handler, PacketByteBuf buf, Consumer<GenericFutureListener<? extends Future<? super Void>>> consumer) {
        PacketByteBuf response = PacketByteBufs.create();
        response.writeVarInt(NoticaNetworking.PROTOCOL_VERSION);

        return CompletableFuture.completedFuture(response);
    }

    private void onPlaySong(PlaySongS2CPacket packet, ClientPlayerEntity player, PacketSender sender) {
        Identifier songId = packet.getSongId();
        byte[] checksum = packet.getChecksum();

        PendingSong song = songRepository.get(checksum);

        if (song == null) {
            logger.debug("Song {} ({}) is not cached, requesting it", songId, ByteHelper.toHexString(checksum, 32));

            // song is not cached, create a new instance
            song = new PendingSong(packet.getHeader());

            SongSlice slice = packet.getSlice();

            logger.debug("Got initial slice {} for song {}", slice, songId);

            song.accept(slice);

            if (!packet.isLast()) {
                requestNext(songId, slice);
            }

            songRepository.add(songId, checksum, song);
        }

        controller.playSong(songId, packet.getVolume(), packet.getStartTick());
    }

    private void onStopSong(StopSongBidiPacket packet, ClientPlayerEntity player, PacketSender sender) {
        Identifier songId = packet.getSongId();

        controller.stopSong(songId);
    }

    private void onMusicOptionsSync(MusicOptionsS2CPacket packet, ClientPlayerEntity player, PacketSender sender) {
        PlayerConfig config = packet.getConfig();
        playerConfig.copyClient(config);
    }

    private void onRespondSong(RespondSongS2CPacket packet, ClientPlayerEntity player, PacketSender sender) {
        Identifier songId = packet.getSongId();
        SongSlice slice = packet.getSlice();

        PendingSong song = songRepository.get(songId);

        if (song == null) {
            logger.debug("Cannot receive song slice for unknown song {}", songId);
            return;
        }

        logger.debug("Received song slice {} for song {}", slice, songId);

        song.accept(slice);

        if (packet.isLast()) {
            logger.debug("Song slice response was the last one. Song request for song {} completed", songId);
        } else {
            requestNext(songId, slice);
        }
    }

    private void requestNext(Identifier songId, SongSlice prev) {
        request(songId, prev.tickEnd(), prev.layerEnd() + 1);
    }

    private void request(Identifier songId, int tickOffset, int layerOffset) {
        if (!ClientPlayNetworking.canSend(RequestSongC2SPacket.TYPE)) {
            logger.debug("Server didn't declare the ability to accept song requests, aborting song request");
            return;
        }

        logger.debug("Requesting song slice {}, {} of song {}", tickOffset, layerOffset, songId);

        RequestSongC2SPacket packet = new RequestSongC2SPacket(songId, tickOffset, layerOffset);

        ClientPlayNetworking.send(packet);
    }
}
