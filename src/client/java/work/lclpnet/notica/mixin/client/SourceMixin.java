package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.Source;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaSource;

import java.util.function.Consumer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

@Mixin(Source.class)
public abstract class SourceMixin implements NoticaSource {

    @Unique private boolean noticaSource = false, stopped = false;
    @Unique private @Nullable Consumer<Source> tickAction = null;

    @Shadow public abstract boolean isStopped();
    @Shadow @Final private int pointer;

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

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void notica$onTick(CallbackInfo ci) {
        if (tickAction != null) {
            tickAction.accept((Source) (Object) this);
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

    @Override
    public float notica$getOffsetSeconds() {
        return alGetSourcef(pointer, AL_SEC_OFFSET);
    }

    @Override
    public int notica$getCompletedBuffers() {
        return alGetSourcei(pointer, AL_BUFFERS_PROCESSED);
    }

    @Override
    public void notica$onTick(@Nullable Consumer<Source> action) {
        this.tickAction = action;
    }
}
