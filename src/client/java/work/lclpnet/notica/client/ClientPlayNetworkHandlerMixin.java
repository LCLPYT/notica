package work.lclpnet.notica.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.ClientJoinGameCallback;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(
            method = "onGameJoin",
            at = @At("HEAD")
    )
    public void notica$onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        ClientPlayNetworkHandler self = (ClientPlayNetworkHandler) (Object) this;
        ClientJoinGameCallback.EVENT.invoker().onJoin(self);
    }
}
