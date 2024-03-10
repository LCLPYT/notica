package work.lclpnet.kibu.nbs.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.kibu.nbs.event.ClientDisconnectCallback;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(
            method = "onDisconnected",
            at = @At("HEAD")
    )
    public void kibu_nbs$onDisconnected(CallbackInfo ci) {
        ClientDisconnectCallback.EVENT.invoker().onDisconnected();
    }
}
