package work.lclpnet.kibu.nbs.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.impl.ClientMusicBackend;
import work.lclpnet.kibu.nbs.impl.ClientSongRepository;
import work.lclpnet.kibu.nbs.network.packet.*;
import work.lclpnet.kibu.nbs.util.ByteHelper;
import work.lclpnet.kibu.nbs.util.PendingSong;
import work.lclpnet.kibu.nbs.util.PlayerConfigEntry;

public class KibuNbsClientNetworking {

    private final ClientSongRepository songRepository;
    private final ClientMusicBackend controller;
    private final PlayerConfigEntry playerConfig;
    private final Logger logger;

    public KibuNbsClientNetworking(ClientSongRepository songRepository, ClientMusicBackend controller,
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

            song.accept(slice);

            if (!packet.isLast()) {
                requestNext(songId, slice);
            }

            songRepository.add(songId, checksum, song);
        }

        controller.playSong(songId, packet.getVolume());
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

        if (song == null) return;

        logger.debug("Received song slice {} for song {}", slice, songId);

        song.accept(slice);

        if (!packet.isLast()) {
            requestNext(songId, slice);
        }
    }

    private void requestNext(Identifier songId, SongSlice prev) {
        request(songId, prev.tickEnd(), prev.layerEnd() + 1);
    }

    private void request(Identifier songId, int tickOffset, int layerOffset) {
        if (!ClientPlayNetworking.canSend(RequestSongC2SPacket.TYPE)) return;

        RequestSongC2SPacket packet = new RequestSongC2SPacket(songId, tickOffset, layerOffset);

        ClientPlayNetworking.send(packet);
    }
}
