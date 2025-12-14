package work.lclpnet.notica.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.ClientDisconnectCallback;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(
            method = "clearDownloadedResourcePacks",
            at = @At("HEAD")
    )
    public void notica$onDisconnected(CallbackInfo ci) {
        ClientDisconnectCallback.EVENT.invoker().onDisconnected();
    }
}
