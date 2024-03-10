package work.lclpnet.kibu.nbs.controller;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Position;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.PlayerHolder;
import work.lclpnet.kibu.nbs.api.SongResolver;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;
import work.lclpnet.kibu.nbs.network.SongHeader;
import work.lclpnet.kibu.nbs.network.SongSlicer;
import work.lclpnet.kibu.nbs.network.packet.PlaySongS2CPacket;

import java.util.Set;

/**
 * A remote controller that delegates the commands to a remote player using networking.
 */
public class RemoteController implements Controller, PlayerHolder {

    private ServerPlayerEntity player;
    private final SongResolver resolver;
    private final Logger logger;

    public RemoteController(ServerPlayerEntity player, SongResolver resolver, Logger logger) {
        this.player = player;
        this.resolver = resolver;
        this.logger = logger;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        synchronized (this) {
            this.player = player;
        }
    }

    @Override
    public void playSong(SongDescriptor descriptor, float volume) {
        Song song = resolver.resolve(descriptor);
        startSong(descriptor, song, volume);
    }

    private void startSong(SongDescriptor descriptor, Song song, float volume) {
        SongHeader header = new SongHeader(song);

        // send the first 5 seconds along with the play packet, so that the client can start playing instantly
        SongSlice slice = SongSlicer.sliceSeconds(song, 5);

        var packet = new PlaySongS2CPacket(descriptor, volume, header, slice);
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public void playSongAt(SongDescriptor song, Position position, float volume) {
        // TODO implement
    }

    @Override
    public void stopSong(SongDescriptor song) {
        // TODO implement
    }

    @Override
    public Set<SongDescriptor> getPlayingSongs() {
        return Set.of();  // TODO implement
    }
}
