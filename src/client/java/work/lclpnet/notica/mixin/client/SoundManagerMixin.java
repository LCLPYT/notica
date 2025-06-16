package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.event.SongVolumeChangedCallback;
import work.lclpnet.notica.impl.ds.Debounce;
import work.lclpnet.notica.type.NoticaSoundSystem;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Shadow @Final private SoundSystem soundSystem;
    @Unique
    private final Debounce debounce = new Debounce(500);

    @Inject(
            method = "updateSoundVolume",
            at = @At("TAIL")
    )
    public void notica$onUpdateSoundVolume(SoundCategory category, float volume, CallbackInfo ci) {
        if (category != SoundCategory.RECORDS) return;

        var noticaSoundSystem = (NoticaSoundSystem) soundSystem;

        if (noticaSoundSystem.notica$isPaused()) {
            noticaSoundSystem.notica$setVolumeChangedWhilePaused();
        } else {
            debounce.debounce(() -> SongVolumeChangedCallback.EVENT.invoker().onVolumeChanged());
        }
    }

    @Inject(
            method = "close",
            at = @At("TAIL")
    )
    public void notica$close(CallbackInfo ci) {
        debounce.shutdown();
    }
}
