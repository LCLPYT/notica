package work.lclpnet.kibu.nbs.mixin.client;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.kibu.nbs.impl.NbsSoundInstance;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Inject(
            method = "getAdjustedPitch",
            at = @At("HEAD"),
            cancellable = true
    )
    public void kibu$modifyAdjustedPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!(sound instanceof NbsSoundInstance)) return;

        // do not clamp pitch, but make sure it is greater than 0
        float pitch = Math.max(sound.getPitch(), 1e-6f);

        cir.setReturnValue(pitch);
    }
}
