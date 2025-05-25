package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.Channel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.notica.type.NoticaSourceManager;

@Mixin(Channel.SourceManager.class)
public class SourceManagerMixin implements NoticaSourceManager {

    @Unique @Nullable
    private Runnable onStopped = null;

    @Override
    public void notica$onStopped(@Nullable Runnable runnable) {
        this.onStopped = runnable;
    }

    @Inject(
            method = "close",
            at = @At("TAIL")
    )
    public void notica$onClose(CallbackInfo ci) {
        if (onStopped != null) {
            onStopped.run();
        }
    }
}
