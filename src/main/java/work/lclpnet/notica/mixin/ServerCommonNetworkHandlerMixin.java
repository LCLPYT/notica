package work.lclpnet.notica.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.ResourcePackStatusCallback;

import java.util.UUID;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {

    @Shadow protected abstract GameProfile getProfile();

    @Shadow @Final protected MinecraftServer server;

    @Inject(
            method = "onResourcePackStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void notica$onResourcePackStatus(ResourcePackStatusC2SPacket packet, CallbackInfo ci) {
        if (server == null) return;

        PlayerManager playerManager = server.getPlayerManager();
        if (playerManager == null) return;

        UUID uuid = getProfile().getId();
        if (uuid == null) return;

        ServerPlayerEntity player = playerManager.getPlayer(uuid);
        if (player == null) return;

        ResourcePackStatusCallback.HOOK.invoker().onResourcePackStatus(player, packet);
    }
}
