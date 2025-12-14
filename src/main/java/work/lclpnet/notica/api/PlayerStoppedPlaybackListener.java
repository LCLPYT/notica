package work.lclpnet.notica.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface PlayerStoppedPlaybackListener {

    Identifier getSongId();

    boolean isListener(ServerPlayer player);

    void onStoppedPlayback(ServerPlayer player);
}
