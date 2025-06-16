package work.lclpnet.notica.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaMusicTracker;

import java.util.function.BooleanSupplier;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin implements NoticaMusicTracker {

    @Shadow private @Nullable SoundInstance current;
    @Shadow @Final private MinecraftClient client;

    @Shadow public abstract void stop();

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

        if (current != null && client.getSoundManager().isPlaying(current)) {
            stop();
        }

        ci.cancel();
    }
}
