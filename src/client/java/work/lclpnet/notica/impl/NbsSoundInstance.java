package work.lclpnet.notica.impl;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public class NbsSoundInstance extends AbstractSoundInstance {

    private final DirectSoundManager directSoundManager;

    public NbsSoundInstance(Identifier id, SoundCategory category, float volume, float pitch, Random random, boolean repeat, int repeatDelay, SoundInstance.AttenuationType attenuationType, double x, double y, double z, boolean relative, DirectSoundManager directSoundManager) {
        super(id, category, random);
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.repeat = repeat;
        this.repeatDelay = repeatDelay;
        this.attenuationType = attenuationType;
        this.relative = relative;
        this.directSoundManager = directSoundManager;
    }

    @Override
    public WeightedSoundSet getSoundSet(SoundManager soundManager) {
        WeightedSoundSet set = super.getSoundSet(soundManager);

        if (set != null) {
            return set;
        }

        // sound is missing, maybe a custom instrument referencing a direct sound file
        set = directSoundManager.getSoundSet(id);

        if (set != null) {
            this.sound = set.getSound(random);
        }

        return set;
    }
}
