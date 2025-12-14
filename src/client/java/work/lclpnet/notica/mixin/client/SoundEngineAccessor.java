package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {

    @Accessor
    SoundBufferLibrary getSoundBuffers();

    @Accessor
    ChannelAccess getChannelAccess();
}
