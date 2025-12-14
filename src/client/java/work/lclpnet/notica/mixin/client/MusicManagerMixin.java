package work.lclpnet.notica.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaMusicManager;

import java.util.function.BooleanSupplier;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin implements NoticaMusicManager {

    @Shadow private @Nullable SoundInstance currentMusic;
    @Shadow @Final private Minecraft minecraft;

    @Shadow public abstract void stopPlaying();

    @Unique
    private BooleanSupplier musicInhibitor = () -> false;

    @Override
    public void notica$setMusicInhibitor(BooleanSupplier inhibitor) {
        this.musicInhibitor = inhibitor;
    }

    @Inject(
            method = "tick",
            at = @At("HEAD"),
            cancellable = true
    )
    public void notica$shouldInhibitMusic(CallbackInfo ci) {
        if (!musicInhibitor.getAsBoolean()) return;

        if (currentMusic != null && minecraft.getSoundManager().isActive(currentMusic)) {
            stopPlaying();
        }

        ci.cancel();
    }
}
