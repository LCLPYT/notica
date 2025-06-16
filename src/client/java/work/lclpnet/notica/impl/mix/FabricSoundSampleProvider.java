package work.lclpnet.notica.impl.mix;

import net.minecraft.client.sound.*;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.impl.DirectSoundManager;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class FabricSoundSampleProvider implements SoundSampleProvider {

    private final Instruments instruments;
    private final InstrumentSoundProvider soundProvider;
    private final SoundManager soundManager;
    private final DirectSoundManager directSoundManager;
    private final ResourceFactory resourceFactory;
    private final Logger logger;
    private final Random random = Random.create(42);

    public FabricSoundSampleProvider(Instruments instruments, InstrumentSoundProvider soundProvider,
                                     SoundManager soundManager, DirectSoundManager directSoundManager,
                                     ResourceFactory resourceFactory, Logger logger) {
        this.instruments = instruments;
        this.soundProvider = soundProvider;
        this.soundManager = soundManager;
        this.directSoundManager = directSoundManager;
        this.resourceFactory = resourceFactory;
        this.logger = logger;
    }

    @Override
    public Optional<SoundRef> getSample(byte instrument) {
        Sound sound = getSound(instrument);

        if (sound == null || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            return Optional.empty();
        }

        if (sound.isStreamed()) {
            logger.warn("Instrument sound {} is a streamed sound and will not be loaded into memory", sound.getLocation());
            return Optional.empty();
        }

        float volume = sound.getVolume().get(random);
        float pitch = sound.getPitch().get(random);

        return Optional.of(new Ref(sound.getLocation(), volume, pitch));
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

    private class Ref implements SoundRef {

        private final Identifier location;
        private final float volume;
        private final float pitch;

        private Ref(Identifier location, float volume, float pitch) {
            this.location = location;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public CompletableFuture<SoundSample> load() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return loadUnifiedSoundSync();
                } catch (IOException | UnsupportedAudioFileException e) {
                    throw new RuntimeException("Failed to load sound " + location, e);
                }
            });
        }

        private SoundSample loadUnifiedSoundSync() throws IOException, UnsupportedAudioFileException {
            try (NonRepeatingAudioStream audioIn = new OggAudioStream(resourceFactory.open(location))) {
                ByteBuffer sample = audioIn.readAll();

                return new SoundSample(sample, audioIn.getFormat());
            }
        }

        @Override
        public float volume() {
            return volume;
        }

        @Override
        public float pitch() {
            return pitch;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Ref) obj;
            return Objects.equals(this.location, that.location) &&
                    Float.floatToIntBits(this.volume) == Float.floatToIntBits(that.volume) &&
                    Float.floatToIntBits(this.pitch) == Float.floatToIntBits(that.pitch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, volume, pitch);
        }

        @Override
        public String toString() {
            return "Ref[location=%s, volume=%s, pitch=%s]".formatted(location, volume, pitch);
        }

    }
}
