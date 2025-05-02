package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.client.sound.SoundManager.EMPTY_ID;

public class SoundSampleManager {

    private final Instruments instruments;
    private final InstrumentSoundProvider soundProvider;
    private final Random random;
    private final SoundManager soundManager;
    private final DirectSoundManager directSoundManager;
    private final UnifiedSoundLoader soundLoader;
    private final ByteBuffer[] samples;

    public SoundSampleManager(Instruments instruments, InstrumentSoundProvider soundProvider, Random random,
                              SoundManager soundManager, DirectSoundManager directSoundManager,
                              UnifiedSoundLoader soundLoader) {
        this.instruments = instruments;
        this.soundProvider = soundProvider;
        this.random = random;
        this.soundManager = soundManager;
        this.directSoundManager = directSoundManager;
        this.soundLoader = soundLoader;

        this.samples = new ByteBuffer[instruments.customBegin() + instruments.custom().length];
    }

    public @Nullable ByteBuffer getSample(byte instrument) {
        return samples[instrument & 0xF];
    }

    public void loadAll() {
        List<CompletableFuture<?>> futures = new ArrayList<>(samples.length);

        for (byte i = 0; i < samples.length; i++) {
            Identifier soundId = getSoundResourceId(i);

            if (soundId == null || EMPTY_ID.equals(soundId)) continue;

            int idx = i & 0xF;

            var future = soundLoader.getUnifiedSample(soundId)
                    .thenAccept(sample -> samples[idx] = sample);

            futures.add(future);
        }

        futures.forEach(CompletableFuture::join);
    }

    private @Nullable Identifier getSoundResourceId(byte instrument) {
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

        Sound sound = soundSet.getSound(random);

        return sound.getIdentifier();
    }
}
