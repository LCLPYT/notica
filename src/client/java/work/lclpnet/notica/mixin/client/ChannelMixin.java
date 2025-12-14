package work.lclpnet.notica.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaChannel;

import java.util.function.Consumer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

@Mixin(Channel.class)
public abstract class ChannelMixin implements NoticaChannel {

    @Unique private boolean noticaSource = false, stopped = false;
    @Unique private @Nullable Consumer<Channel> tickAction = null;

    @Shadow public abstract boolean stopped();
    @Shadow @Final private int source;

    @Inject(
            method = "pumpBuffers",
            at = @At("HEAD"),
            cancellable = true
    )
    private void notica$preventReadingStoppedSources(int count, CallbackInfo ci) {
        if (noticaSource && (stopped || stopped())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "updateStream",
            at = @At("HEAD")
    )
    private void notica$onTick(CallbackInfo ci) {
        if (tickAction != null) {
            tickAction.accept((Channel) (Object) this);
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
        return alGetSourcef(source, AL_SEC_OFFSET);
    }

    @Override
    public int notica$getCompletedBuffers() {
        return alGetSourcei(source, AL_BUFFERS_PROCESSED);
    }

    @Override
    public void notica$onTick(@Nullable Consumer<Channel> action) {
        this.tickAction = action;
    }
}
