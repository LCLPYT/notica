package work.lclpnet.notica.impl;

import net.minecraft.client.sound.NonRepeatingAudioStream;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
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
import java.util.concurrent.CompletableFuture;

/**
 * Loads sound resources in a unified audio format.
 */
public class UnifiedSoundLoader {

    private final ResourceFactory resourceFactory;
    private final AudioFormat targetFormat;
    private final Logger logger;
    private final Map<Identifier, CompletableFuture<ByteBuffer>> unifiedSamples = new HashMap<>();

    public UnifiedSoundLoader(ResourceFactory resourceFactory, AudioFormat targetFormat, Logger logger) {
        this.resourceFactory = resourceFactory;
        this.targetFormat = targetFormat;
        this.logger = logger;
    }

    /**
     * Gets the sound sample for a given sound identifier, loaded in a unified format.
     * If there is no sample yet, the sound will be loaded using the resource factory.
     * @param soundId The sound identifier.
     * @return A future of the optional unified sample as {@link ByteBuffer} (buffer will be null if something went wrong).
     */
    public synchronized CompletableFuture<@Nullable ByteBuffer> getUnifiedSample(Identifier soundId) {
        CompletableFuture<ByteBuffer> future = unifiedSamples.get(soundId);

        if (future != null) {
            return future;
        }

        future = loadSound(soundId).exceptionally(err -> {
            logger.error("Failed to get unified sample for sound id {}", soundId, err);

            synchronized (this) {
                unifiedSamples.remove(soundId);
            }

            return null;
        });

        unifiedSamples.put(soundId, future);

        return future;
    }

    private CompletableFuture<ByteBuffer> loadSound(Identifier id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadSoundSync(id);
            } catch (IOException | UnsupportedAudioFileException e) {
                throw new RuntimeException("Failed to load unified sound", e);
            }
        });
    }

    private ByteBuffer loadSoundSync(Identifier id) throws IOException, UnsupportedAudioFileException {
        try (NonRepeatingAudioStream audioIn = new OggAudioStream(resourceFactory.open(id))) {
            ByteBuffer sample = audioIn.readAll();

            return recode(sample, audioIn.getFormat());
        }
    }

    private ByteBuffer recode(ByteBuffer source, AudioFormat srcFormat) throws UnsupportedAudioFileException, IOException {
        ByteOrder order = targetFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        if (targetFormat.matches(srcFormat)) {
            return source.order(order);
        }

        var sourceIn = new AudioInputStream(new ByteBufferInputStream(source), srcFormat, source.limit());
        var convertedIn = AudioSystem.getAudioInputStream(targetFormat, sourceIn);

        byte[] convertedBytes = convertedIn.readAllBytes();

        return ByteBuffer.wrap(convertedBytes).order(order);
    }
}
