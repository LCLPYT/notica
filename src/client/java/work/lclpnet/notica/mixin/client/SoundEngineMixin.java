package work.lclpnet.notica.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.notica.impl.NbsSoundInstance;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(
            method = "calculatePitch",
            at = @At("HEAD"),
            cancellable = true
    )
    public void notica$modifyAdjustedPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!(sound instanceof NbsSoundInstance)) return;

        // do not clamp pitch, but make sure it is greater than 0
        float pitch = Math.max(sound.getPitch(), 1e-6f);

        cir.setReturnValue(pitch);
    }

    // modify default attenuation distance
    @ModifyVariable(
            method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getSource()Lnet/minecraft/sounds/SoundSource;"
            ),
            ordinal = 1  // may change in the future
    )
    public float notica$modifyAttenuationDistance(float distance, @Local(argsOnly = true) SoundInstance soundInstance, @Local Sound sound) {
        if (!(soundInstance instanceof NbsSoundInstance nbs)) return distance;

        float range = nbs.getRange();

        // replace default range
        return distance * range / (float) sound.getAttenuationDistance();
    }
}
