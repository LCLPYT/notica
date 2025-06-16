package work.lclpnet.notica.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.kibu.networking.protocol.ClientProtocolHandler;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.event.SongVolumeChangedCallback;
import work.lclpnet.notica.impl.ClientMusicBackend;
import work.lclpnet.notica.impl.ClientSongRepository;
import work.lclpnet.notica.impl.PendingSong;
import work.lclpnet.notica.network.NoticaNetworking;
import work.lclpnet.notica.network.packet.*;
import work.lclpnet.notica.util.ByteHelper;
import work.lclpnet.notica.util.PlayerConfigEntry;

import static net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver;

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
        new ClientProtocolHandler(NoticaNetworking.PROTOCOL, logger).register();

        registerGlobalReceiver(PlaySongS2CPacket.ID, this::onPlaySong);
        registerGlobalReceiver(RespondSongS2CPacket.ID, this::onRespondSong);
        registerGlobalReceiver(StopSongBidiPacket.ID, this::onStopSong);
        registerGlobalReceiver(MusicOptionsS2CPacket.ID, this::onMusicOptionsSync);
        registerGlobalReceiver(SongSeekS2CPacket.ID, this::onSongSeek);
    }

    private void onPlaySong(PlaySongS2CPacket payload, ClientPlayNetworking.Context context) {
        Thread.startVirtualThread(() -> playSong(payload));
    }

    private void playSong(PlaySongS2CPacket payload) {
        Identifier songId = payload.getSongId();
        byte[] checksum = payload.checksum();
        int startTick = payload.getStartTick();

        PendingSong song = songRepository.get(checksum);

        if (song == null) {
            song = acceptUnknownSong(payload, songId, checksum, startTick);
        } else if (startTick < song.getStartTick()) {
            // the cached song is missing parts before its old start
            acceptUnknownRegion(payload, song, songId);
        }

        controller.playSong(song, songId, payload.getPlaybackOptions(), startTick);
    }

    private @NotNull PendingSong acceptUnknownSong(PlaySongS2CPacket packet, Identifier songId, byte[] checksum, int startTick) {
        logger.debug("Song {} ({}) is not cached, requesting it...", songId, ByteHelper.toHexString(checksum, 32));

        // song is not cached, create a new instance
        PendingSong song = new PendingSong(packet.header(), checksum, startTick);

        SongSlice slice = packet.slice();

        logger.debug("Got initial slice {} for song {}", slice, songId);

        song.accept(slice);

        if (song.loopConfig().enabled() && startTick > 0) {
            // songs with looping enabled that start with an offset need to be fetched completely
            request(songId, 0, 0);
        } else if (!packet.last()) {
            // request next song part
            requestNext(songId, slice);
        }

        songRepository.add(song);

        return song;
    }

    private void acceptUnknownRegion(PlaySongS2CPacket packet, PendingSong song, Identifier songId) {
        SongSlice slice = packet.slice();

        song.accept(slice);

        if (packet.last()) return;

        logger.debug("Cached song is missing parts, requesting the song from the beginning...");

        request(songId, 0, 0);
    }

    private void onStopSong(StopSongBidiPacket payload, ClientPlayNetworking.Context context) {
        Identifier songId = payload.songId();
        controller.stopSong(songId);
    }

    private void onMusicOptionsSync(MusicOptionsS2CPacket payload, ClientPlayNetworking.Context context) {
        PlayerConfig config = payload.config();
        playerConfig.copyClient(config);

        context.client().execute(() -> SongVolumeChangedCallback.EVENT.invoker().onVolumeChanged());
    }

    private void onSongSeek(SongSeekS2CPacket payload, ClientPlayNetworking.Context context) {
        controller.seekSongTo(payload.songId(), payload.ticks(), payload.absolute());
    }

    private void onRespondSong(RespondSongS2CPacket payload, ClientPlayNetworking.Context context) {
        Thread.startVirtualThread(() -> receiveSong(payload));
    }

    private void receiveSong(RespondSongS2CPacket payload) {
        Identifier songId = payload.songId();
        SongSlice slice = payload.slice();

        PendingSong song = songRepository.get(songId);

        if (song == null) {
            logger.debug("Cannot receive song slice for unknown song {}", songId);
            return;
        }

        logger.debug("Received song slice {} for song {}", slice, songId);

        song.accept(slice);

        if (payload.last()) {
            logger.debug("Song slice response was the last one. Song request for song {} completed", songId);
        } else {
            requestNext(songId, slice);
        }
    }

    private void requestNext(Identifier songId, SongSlice prev) {
        request(songId, prev.tickEnd(), prev.layerEnd() + 1);
    }

    private void request(Identifier songId, int tickOffset, int layerOffset) {
        if (!ClientPlayNetworking.canSend(RequestSongC2SPacket.ID)) {
            logger.debug("Server didn't declare the ability to accept song requests, aborting song request");
            return;
        }

        logger.debug("Requesting song slice {}, {} of song {}", tickOffset, layerOffset, songId);

        RequestSongC2SPacket packet = new RequestSongC2SPacket(songId, tickOffset, layerOffset);

        ClientPlayNetworking.send(packet);
    }
}
