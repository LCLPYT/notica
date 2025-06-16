package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.notica.event.SongVolumeChangedCallback;
import work.lclpnet.notica.impl.NbsSoundInstance;
import work.lclpnet.notica.type.NoticaSoundSystem;

@Mixin(SoundSystem.class)
public class SoundSystemMixin implements NoticaSoundSystem {

    @Unique private boolean paused = false, volumeChangedWhilePaused = false;

    @Inject(
            method = "getAdjustedPitch",
            at = @At("HEAD"),
            cancellable = true
    )
    public void notica$modifyAdjustedPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!(sound instanceof NbsSoundInstance)) return;

        // do not clamp pitch, but make sure it is greater than 0
        float pitch = Math.max(sound.getPitch(), 1e-6f);

        cir.setReturnValue(pitch);
    }

    @Inject(
            method = "pauseAll",
            at = @At("HEAD")
    )
    public void notica$onPause(CallbackInfo ci) {
        paused = true;
    }

    @Inject(
            method = "resumeAll",
            at = @At("HEAD")
    )
    public void notica$onResume(CallbackInfo ci) {
        paused = false;

        if (volumeChangedWhilePaused) {
            volumeChangedWhilePaused = false;

            SongVolumeChangedCallback.EVENT.invoker().onVolumeChanged();
        }
    }

    @Override
    public boolean notica$isPaused() {
        return paused;
    }

    @Override
    public void notica$setVolumeChangedWhilePaused() {
        volumeChangedWhilePaused = true;
    }
}
