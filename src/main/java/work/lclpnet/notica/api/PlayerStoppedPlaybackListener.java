package work.lclpnet.notica.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface PlayerStoppedPlaybackListener {

    ResourceLocation getSongId();

    boolean isListener(ServerPlayer player);

    void onStoppedPlayback(ServerPlayer player);
}
