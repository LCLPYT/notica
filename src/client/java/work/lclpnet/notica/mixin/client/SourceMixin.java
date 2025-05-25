package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Source.class)
public abstract class SourceMixin {

    @Shadow public abstract boolean isStopped();

    @Inject(
            method = "read",
            at = @At("HEAD"),
            cancellable = true
    )
    private void notica$preventReadingStoppedSources(int count, CallbackInfo ci) {
        if (isStopped()) {
            ci.cancel();
        }
    }
}
