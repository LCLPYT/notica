package work.lclpnet.kibu.nbs.mixin;

import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEvent.class)
public interface SoundEventAccessor {

    @Accessor
    float getDistanceToTravel();

    @Accessor
    boolean isStaticDistance();
}
