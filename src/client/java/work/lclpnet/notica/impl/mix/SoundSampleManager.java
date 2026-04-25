package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.data.Instruments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public class SoundSampleManager {

    private final SoundSampleProvider sampleProvider;
    private final UnifiedSoundLoader soundLoader;
    private final UnaryOperator<float[]> transformer;
    private final float[][] samples;

    /**
     * @param instruments The instruments used in the song.
     * @param sampleProvider The sound sample provider that provides sound buffers for the instruments.
     * @param soundLoader The unified sound loader converts the sound buffer into a uniform audio format.
     * @param transformer A function to transform the raw float sample array.
     *                    Is applied after the uniform sound loader normalized the audio sample.
     *                    The input audio sample is in a de-interleaved format.
     *                    Meaning: Each channel is continuous in memory, whereas most audio formats store them interleaved as frames.
     */
    public SoundSampleManager(Instruments instruments, SoundSampleProvider sampleProvider, UnifiedSoundLoader soundLoader,
                              UnaryOperator<float[]> transformer) {
        this.sampleProvider = sampleProvider;
        this.soundLoader = soundLoader;

        this.samples = new float[instruments.customBegin() + instruments.custom().length][0];
        this.transformer = transformer;
    }

    public float[] getSample(byte instrument) {
        return samples[instrument & 0xFF];
    }

    /**
     * Preloads all sound samples that are contained within the song.
     * Samples are loaded into a uniform format using {@link UnifiedSoundLoader}.
     * The {@link SoundSampleProvider} implementation decides which instruments are supported.
     */
    public void loadAll() {
        List<CompletableFuture<?>> futures = new ArrayList<>(samples.length);

        for (byte i = 0; i < samples.length; i++) {
            SoundRef sound = sampleProvider.getSample(i).orElse(null);

            if (sound == null) continue;

            final int idx = i & 0xFF;

            var future = soundLoader.getUnifiedSample(sound)
                    .thenAccept(opt -> opt
                            .map(transformer)
                            .ifPresent(sample -> samples[idx] = sample));

            futures.add(future);
        }

        futures.forEach(CompletableFuture::join);
    }
}
