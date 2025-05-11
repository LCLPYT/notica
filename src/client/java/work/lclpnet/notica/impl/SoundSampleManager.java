package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SoundSampleManager {

    private final Instruments instruments;
    private final InstrumentSoundProvider soundProvider;
    private final Random random;
    private final SoundManager soundManager;
    private final DirectSoundManager directSoundManager;
    private final UnifiedSoundLoader soundLoader;
    private final Logger logger;
    private final float[][] samples;

    public SoundSampleManager(Instruments instruments, InstrumentSoundProvider soundProvider, Random random,
                              SoundManager soundManager, DirectSoundManager directSoundManager,
                              UnifiedSoundLoader soundLoader, Logger logger) {
        this.instruments = instruments;
        this.soundProvider = soundProvider;
        this.random = random;
        this.soundManager = soundManager;
        this.directSoundManager = directSoundManager;
        this.soundLoader = soundLoader;
        this.logger = logger;

        this.samples = new float[instruments.customBegin() + instruments.custom().length][0];
    }

    public float[] getSample(byte instrument) {
        return samples[instrument & 0xFF];
    }

    public void loadAll() {
        List<CompletableFuture<?>> futures = new ArrayList<>(samples.length);

        for (byte i = 0; i < samples.length; i++) {
            Sound sound = getSound(i);

            if (sound == null || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) continue;

            if (sound.isStreamed()) {
                logger.warn("Instrument sound {} is a streamed sound and will not be loaded into memory", sound.getLocation());
                continue;
            }

            final int idx = i & 0xFF;

            var future = soundLoader.getUnifiedSample(sound).thenAccept(opt -> opt
                    .ifPresent(sample -> samples[idx] = sample));

            futures.add(future);
        }

        futures.forEach(CompletableFuture::join);
    }

    private @Nullable Sound getSound(byte instrument) {
        CustomInstrument custom = instruments.custom(instrument);
        SoundEvent soundEvent;

        if (custom != null) {
            soundEvent = soundProvider.getCustomInstrumentSound(custom);
        } else {
            soundEvent = soundProvider.getVanillaInstrumentSound(instrument);
        }

        if (soundEvent == null) {
            return null;
        }

        WeightedSoundSet soundSet = soundManager.get(soundEvent.id());

        if (soundSet == null) {
            soundSet = directSoundManager.getSoundSet(soundEvent.id());
        }

        if (soundSet == null) {
            return null;
        }

        return soundSet.getSound(random);
    }
}
