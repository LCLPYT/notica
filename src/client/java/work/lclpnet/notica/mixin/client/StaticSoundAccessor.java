package work.lclpnet.notica.mixin.client;

import net.minecraft.client.sound.StaticSound;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

@Mixin(StaticSound.class)
public interface StaticSoundAccessor {

    @Accessor
    @Nullable ByteBuffer getSample();

    @Accessor
    AudioFormat getFormat();
}
