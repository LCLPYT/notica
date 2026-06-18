package work.lclpnet.notica.mixin.client;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.kibu.config.ConfigManager;
import work.lclpnet.notica.NoticaClientInit;

@Mixin(Listener.class)
public class ListenerMixin {

    @Unique
    private boolean listenerVelocityWasEnabled = false;

    @Inject(
            method = "setTransform",
            at = @At("TAIL")
    )
    public void notica$setListenerVelocity(ListenerTransform transform, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) return;

        var config = NoticaClientInit.configManager()
                .map(ConfigManager::config)
                .orElse(null);

        if (config == null || !config.isListenerVelocity()) {
            if (listenerVelocityWasEnabled) {
                listenerVelocityWasEnabled = false;

                // reset
                AL10.alListener3f(AL10.AL_VELOCITY, 0f, 0f, 0f);
            }

            return;
        }

        listenerVelocityWasEnabled = true;

        Vec3 deltaMovement = player.getDeltaMovement();

        double intensity = config.getDopplerIntensity();

        AL10.alListener3f(
                AL10.AL_VELOCITY,
                (float) (deltaMovement.x * 20 * intensity),
                (float) (deltaMovement.y * 20 * intensity),
                (float) (deltaMovement.z * 20 * intensity)
        );
    }
}
