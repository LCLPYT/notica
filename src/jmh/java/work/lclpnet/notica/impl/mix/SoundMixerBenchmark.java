package work.lclpnet.notica.impl.mix;

import org.openjdk.jmh.annotations.Benchmark;

public class SoundMixerBenchmark {

    @Benchmark
    public void loop() {
        long j = 0;

        for (int i = 0; i < 1_000_000; i++) {
            j += i;
        }
    }
}
