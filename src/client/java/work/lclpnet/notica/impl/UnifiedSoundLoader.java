package work.lclpnet.notica.impl;

import net.minecraft.client.sound.NonRepeatingAudioStream;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.util.ByteBufferInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loads sound resources in a unified audio format.
 */
public class UnifiedSoundLoader {

    private static final float INV_SHORT = 1.f / 32768.f;

    private final ResourceFactory resourceFactory;
    private final AudioFormat targetFormat;
    private final Logger logger;
    private final Map<Identifier, CompletableFuture<Optional<float[]>>> unifiedSamples = new HashMap<>();

    public UnifiedSoundLoader(ResourceFactory resourceFactory, AudioFormat targetFormat, Logger logger) {
        this.resourceFactory = resourceFactory;
        this.targetFormat = targetFormat;
        this.logger = logger;
    }

    /**
     * Gets the sound sample for a given sound identifier, loaded in a unified format.
     * If there is no sample yet, the sound will be loaded using the resource factory.
     * @param sound The sound instance.
     * @return A future of the optional unified sample as {@link ByteBuffer} (buffer will be null if something went wrong).
     */
    public synchronized CompletableFuture<Optional<float[]>> getUnifiedSample(Sound sound) {
        Identifier resourceId = sound.getLocation();

        CompletableFuture<Optional<float[]>> future = unifiedSamples.get(resourceId);

        if (future != null) {
            return future;
        }

        future = loadUnifiedSound(resourceId)
                .thenApply(sample -> transformSample(sample, sound))
                .thenApply(this::toDeinterleavedFloats)
                .thenApply(Optional::of)
                .exceptionally(err -> {
                    logger.error("Failed to get unified sample for sound id {}", resourceId, err);

                    synchronized (this) {
                        unifiedSamples.remove(resourceId);
                    }

                    return Optional.empty();
                });

        unifiedSamples.put(resourceId, future);

        return future;
    }

    private float[] toDeinterleavedFloats(ByteBuffer samples) {
        final int channels = 2;
        int frameCount = samples.limit() / targetFormat.getFrameSize();

        samples.position(0);

        float[] floatSamples = new float[frameCount * channels];

        for (int i = 0; i < frameCount; i++) {
            short ql = samples.getShort();
            short qr = samples.getShort();

            float cl = ql * INV_SHORT;
            float cr = qr * INV_SHORT;

            floatSamples[i] = cl;
            floatSamples[i + frameCount] = cr;
        }

        return floatSamples;
    }

    private CompletableFuture<ByteBuffer> loadUnifiedSound(Identifier id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadUnifiedSoundSync(id);
            } catch (IOException | UnsupportedAudioFileException e) {
                throw new RuntimeException("Failed to load unified sound", e);
            }
        });
    }

    private ByteBuffer loadUnifiedSoundSync(Identifier id) throws IOException, UnsupportedAudioFileException {
        try (NonRepeatingAudioStream audioIn = new OggAudioStream(resourceFactory.open(id))) {
            ByteBuffer sample = audioIn.readAll();

            return recode(sample, audioIn.getFormat());
        }
    }

    private ByteBuffer recode(ByteBuffer source, AudioFormat srcFormat) throws IOException {
        ByteOrder order = targetFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        if (targetFormat.matches(srcFormat)) {
            return source.order(order);
        }

        var sourceIn = new AudioInputStream(new ByteBufferInputStream(source), srcFormat, source.limit());
        var convertedIn = AudioSystem.getAudioInputStream(targetFormat, sourceIn);

        byte[] convertedBytes = convertedIn.readAllBytes();

        return ByteBuffer.wrap(convertedBytes).order(order);
    }

    /**
     * Applies static sound transformation for volume and pitch, according to the definition in sounds.json.
     * @param sample The sound samples, in the unified format.
     * @param sound The sound configuration from sounds.json.
     * @return The transformed sample.
     */
    private ByteBuffer transformSample(ByteBuffer sample, Sound sound) {
        Random random = Random.create(42L);
        float volume = sound.getVolume().get(random);
        float pitch = sound.getPitch().get(random);

        sample = SoundMixer.changePitch(sample, pitch, targetFormat, ByteBuffer::allocate);

        SoundMixer.changeVolume(sample, volume);

        return sample;
    }
}
