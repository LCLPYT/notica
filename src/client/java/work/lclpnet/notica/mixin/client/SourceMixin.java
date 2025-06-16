package work.lclpnet.notica.mixin.client;

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
    private boolean noticaSource = false, stopped = false;

    @Shadow public abstract boolean isStopped();

    @Inject(
            method = "read",
            at = @At("HEAD"),
            cancellable = true
    )
    private void notica$preventReadingStoppedSources(int count, CallbackInfo ci) {
        if (noticaSource && (stopped || isStopped())) {
            ci.cancel();
        }
    }

    @Override
    public void notica$setNoticaSource() {
        noticaSource = true;
    }

    @Override
    public void notica$setStopped() {
        stopped = true;
    }
}
