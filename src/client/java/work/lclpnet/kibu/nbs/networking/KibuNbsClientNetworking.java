package work.lclpnet.kibu.nbs.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.impl.ClientController;
import work.lclpnet.kibu.nbs.impl.ClientSongResolver;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;
import work.lclpnet.kibu.nbs.network.packet.PlaySongS2CPacket;
import work.lclpnet.kibu.nbs.network.packet.RequestSongC2SPacket;
import work.lclpnet.kibu.nbs.network.packet.RespondSongS2CPacket;
import work.lclpnet.kibu.nbs.util.PendingSong;

public class KibuNbsClientNetworking {

    private final ClientSongResolver songRepository;
    private final ClientController controller;
    private final Logger logger;

    public KibuNbsClientNetworking(ClientSongResolver songRepository, ClientController controller, Logger logger) {
        this.songRepository = songRepository;
        this.controller = controller;
        this.logger = logger;
    }

    public void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlaySongS2CPacket.TYPE, this::onPlaySong);
        ClientPlayNetworking.registerGlobalReceiver(RespondSongS2CPacket.TYPE, this::onRespondSong);
    }

    private void onPlaySong(PlaySongS2CPacket packet, ClientPlayerEntity player, PacketSender sender) {
        SongDescriptor descriptor = packet.getSongDescriptor();

        PendingSong song = songRepository.resolve(descriptor);

        if (song == null) {
            logger.debug("Song {} is not cached, requesting it", descriptor);

            // song is not cached, create a new instance
            song = new PendingSong(packet.getHeader());

            SongSlice slice = packet.getSlice();

            if (song.accept(slice)) {
                requestNext(descriptor.id(), slice);
            }

            songRepository.add(descriptor, song);
        }

        controller.playSong(descriptor, packet.getVolume());
    }

    private void onRespondSong(RespondSongS2CPacket packet, ClientPlayerEntity player, PacketSender sender) {
        Identifier songId = packet.getSongId();
        SongSlice slice = packet.getSlice();

        PendingSong song = songRepository.resolve(songId);

        if (song == null) return;

        logger.debug("Received song slice {} for song {}", slice, songId);

        if (song.accept(slice)) {
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
