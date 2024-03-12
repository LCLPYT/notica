package work.lclpnet.kibu.nbs.api;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public interface PlayerStoppedPlaybackListener {

    Identifier getSongId();

    boolean isListener(ServerPlayerEntity player);

    void onStoppedPlayback(ServerPlayerEntity player);
}
