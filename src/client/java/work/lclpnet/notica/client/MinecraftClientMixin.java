package work.lclpnet.notica.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.ClientDisconnectCallback;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(
            method = "onDisconnected",
            at = @At("HEAD")
    )
    public void notica$onDisconnected(CallbackInfo ci) {
        ClientDisconnectCallback.EVENT.invoker().onDisconnected();
    }
}
