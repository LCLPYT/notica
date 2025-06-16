package work.lclpnet.notica.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaSource;

@Mixin(Source.class)
public abstract class SourceMixin implements NoticaSource {

    @Unique
    private boolean noticaSource = false, seeking = false;

    @Shadow public abstract boolean isStopped();

    @Shadow protected abstract void read(int count);

    @Inject(
            method = "read",
            at = @At("HEAD"),
            cancellable = true
    )
    private void notica$preventReadingStoppedSources(int count, CallbackInfo ci) {
        if (noticaSource && isStopped()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/AudioStream;close()V"
            )
    )
    private void notica$preventClosingSeeking(AudioStream instance, Operation<Void> original) {
        if (!noticaSource || !seeking) {
            original.call(instance);
        }
    }

    @Override
    public void notica$setNoticaSource() {
        noticaSource = true;
    }

    @Override
    public void notica$setSeeking() {
        seeking = true;
    }
}
