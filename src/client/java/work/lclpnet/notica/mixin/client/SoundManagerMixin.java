package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.SongVolumeChangedCallback;
import work.lclpnet.notica.impl.ds.Debounce;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Unique
    private final Debounce debounce = new Debounce(500);

    @Inject(
            method = "refreshCategoryVolume",
            at = @At("TAIL")
    )
    public void notica$onUpdateSoundVolume(SoundSource soundCategory, CallbackInfo ci) {
        if (soundCategory != SoundSource.RECORDS) return;

        debounce.debounce(() -> SongVolumeChangedCallback.EVENT.invoker().onVolumeChanged());
    }

    @Inject(
            method = "destroy",
            at = @At("TAIL")
    )
    public void notica$close(CallbackInfo ci) {
        debounce.shutdown();
    }
}
