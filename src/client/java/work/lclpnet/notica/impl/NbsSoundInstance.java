package work.lclpnet.notica.impl;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class NbsSoundInstance extends AbstractSoundInstance {

    private final DirectSoundManager directSoundManager;

    public NbsSoundInstance(Identifier id, SoundSource category, float volume, float pitch, RandomSource random, boolean repeat, int repeatDelay, SoundInstance.Attenuation attenuationType, double x, double y, double z, boolean relative, DirectSoundManager directSoundManager) {
        super(id, category, random);
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = repeat;
        this.delay = repeatDelay;
        this.attenuation = attenuationType;
        this.relative = relative;
        this.directSoundManager = directSoundManager;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        WeighedSoundEvents set = super.resolve(soundManager);

        if (set != null) {
            return set;
        }

        // sound is missing, maybe a custom instrument referencing a direct sound file
        set = directSoundManager.getSoundSet(identifier);

        if (set != null) {
            this.sound = set.getSound(random);
        }

        return set;
    }
}
