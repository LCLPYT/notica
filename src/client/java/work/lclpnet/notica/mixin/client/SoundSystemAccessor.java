package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {

    @Accessor
    SoundLoader getSoundLoader();

    @Accessor
    Channel getChannel();

    @Invoker
    float invokeGetSoundVolume(@Nullable SoundCategory category);
}
