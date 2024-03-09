package work.lclpnet.kibu.nbs.controller;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Position;
import work.lclpnet.kibu.nbs.api.PlayerHolder;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

import java.util.Set;

/**
 * A remote controller that delegates the commands to a remote player using networking.
 */
public class RemoteController implements Controller, PlayerHolder {

    private ServerPlayerEntity player;

    public RemoteController(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        synchronized (this) {
            this.player = player;
        }
    }

    @Override
    public void playSong(SongDescriptor song, float volume) {
//        var packet = new PlaySongS2CPacket(song, volume);
//        ServerPlayNetworking.send(player, packet);
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
