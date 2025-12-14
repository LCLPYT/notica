package work.lclpnet.notica.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.ClientJoinGameCallback;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(
            method = "handleLogin",
            at = @At("HEAD")
    )
    public void notica$onGameJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        ClientJoinGameCallback.EVENT.invoker().onJoin(self);
    }
}
