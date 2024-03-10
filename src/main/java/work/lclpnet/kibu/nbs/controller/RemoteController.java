package work.lclpnet.kibu.nbs.controller;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Position;
import work.lclpnet.kibu.nbs.api.PlayerHolder;
import work.lclpnet.kibu.nbs.api.SongResolver;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;
import work.lclpnet.kibu.nbs.network.SongHeader;
import work.lclpnet.kibu.nbs.network.SongSlicer;
import work.lclpnet.kibu.nbs.network.packet.PlaySongS2CPacket;
import work.lclpnet.kibu.nbs.network.packet.StopSongBidiPacket;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A remote controller that delegates the commands to a remote player using networking.
 */
public class RemoteController implements Controller, PlayerHolder {

    private ServerPlayerEntity player;
    private final SongResolver resolver;
    private final Set<SongDescriptor> playing = new HashSet<>();

    public RemoteController(ServerPlayerEntity player, SongResolver resolver) {
        this.player = player;
        this.resolver = resolver;
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

        if (song == null) return;

        startSong(descriptor, song, volume);
    }

    private void startSong(SongDescriptor descriptor, Song song, float volume) {
        SongHeader header = new SongHeader(song);

        // send the first 5 seconds along with the play packet, so that the client can start playing instantly
        SongSlice slice = SongSlicer.sliceSeconds(song, 5);
        boolean finished = SongSlicer.isFinished(song, slice);

        var packet = new PlaySongS2CPacket(descriptor, volume, header, finished, slice);
        ServerPlayNetworking.send(player, packet);

        playing.add(descriptor);
    }

    @Override
    public void playSongAt(SongDescriptor song, Position position, float volume) {
        // TODO implement
    }

    @Override
    public void stopSong(SongDescriptor song) {
        if (!playing.remove(song)) return;

        var packet = new StopSongBidiPacket(song.id());
        ServerPlayNetworking.send(player, packet);
    }

    public void removePlaying(Identifier id) {
        playing.removeIf(song -> song.id().equals(id));
    }

    @Override
    public Set<SongDescriptor> getPlayingSongs() {
        return Collections.unmodifiableSet(playing);
    }
}
