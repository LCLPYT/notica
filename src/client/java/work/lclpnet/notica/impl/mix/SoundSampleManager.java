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
