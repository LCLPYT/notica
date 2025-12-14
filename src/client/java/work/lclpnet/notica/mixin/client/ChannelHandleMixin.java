package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sounds.ChannelAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaChannelHandle;

@Mixin(ChannelAccess.ChannelHandle.class)
public class ChannelHandleMixin implements NoticaChannelHandle {

    @Unique @Nullable
    private Runnable onStopped = null;

    @Override
    public void notica$onStopped(@Nullable Runnable runnable) {
        this.onStopped = runnable;
    }

    @Inject(
            method = "release",
            at = @At("TAIL")
    )
    public void notica$onClose(CallbackInfo ci) {
        if (onStopped != null) {
            onStopped.run();
        }
    }
}
