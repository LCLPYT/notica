package work.lclpnet.notica.event;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ResourcePackStatusCallback {

    Hook<ResourcePackStatusCallback> HOOK = HookFactory.createArrayBacked(ResourcePackStatusCallback.class, callbacks -> (player, packet) -> {
        for (ResourcePackStatusCallback callback : callbacks) {
            callback.onResourcePackStatus(player, packet);
        }
    });

    void onResourcePackStatus(ServerPlayer player, ServerboundResourcePackPacket packet);
}
