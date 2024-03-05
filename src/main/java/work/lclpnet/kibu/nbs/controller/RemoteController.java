package work.lclpnet.kibu.nbs.controller;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.Position;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

/**
 * A remote controller that delegates the commands to a remote player using networking.
 */
public class RemoteController implements Controller {

    private final ServerPlayNetworkHandler networkHandler;

    public RemoteController(ServerPlayNetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    @Override
    public void playSong(SongDescriptor song, float volume) {
        // TODO implement
    }

    @Override
    public void playSongAt(SongDescriptor song, Position position, float volume) {
        // TODO implement
    }
}
