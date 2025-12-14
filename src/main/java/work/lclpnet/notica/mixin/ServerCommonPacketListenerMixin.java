package work.lclpnet.notica.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.ResourcePackStatusCallback;

import java.util.UUID;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin {

    @Shadow protected abstract GameProfile playerProfile();

    @Shadow @Final protected MinecraftServer server;

    @Inject(
            method = "handleResourcePackResponse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void notica$onResourcePackStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
        if (server == null) return;

        PlayerList playerManager = server.getPlayerList();

        UUID uuid = playerProfile().id();
        if (uuid == null) return;

        ServerPlayer player = playerManager.getPlayer(uuid);
        if (player == null) return;

        ResourcePackStatusCallback.HOOK.invoker().onResourcePackStatus(player, packet);
    }
}
