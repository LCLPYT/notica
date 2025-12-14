package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundBufferLibrary.class)
public interface SoundLoaderAccessor {

    @Accessor
    ResourceProvider getResourceManager();
}
