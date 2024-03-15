package work.lclpnet.notica.event;

import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ResourcePackStatusCallback {

    Hook<ResourcePackStatusCallback> HOOK = HookFactory.createArrayBacked(ResourcePackStatusCallback.class, callbacks -> (player, packet) -> {
        for (ResourcePackStatusCallback callback : callbacks) {
            callback.onResourcePackStatus(player, packet);
        }
    });

    void onResourcePackStatus(ServerPlayerEntity player, ResourcePackStatusC2SPacket packet);
}
