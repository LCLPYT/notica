package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {

    @Accessor
    SoundLoader getSoundLoader();

    @Accessor
    Channel getChannel();
}
