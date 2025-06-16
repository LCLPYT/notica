package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.SongVolumeChangedCallback;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(
            method = "updateSoundVolume",
            at = @At("TAIL")
    )
    public void notica$onUpdateSoundVolume(SoundCategory category, float volume, CallbackInfo ci) {
        if (category != SoundCategory.RECORDS) return;

        SongVolumeChangedCallback.EVENT.invoker().onVolumeChanged();
    }
}
