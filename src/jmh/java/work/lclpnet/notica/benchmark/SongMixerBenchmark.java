package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SongMixer;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"FieldMayBeFinal", "DuplicatedCode"})
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
public class SongMixerBenchmark {

    @State(Scope.Benchmark)
    public static class SongMixerState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            soundMixer = TestUtil.createSoundMixer(song);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            soundMixer.preloadSounds().join();

            endTick = song.tempo().durationTicks(0, 5);
        }
    }

    @Benchmark
    public void baseline(SongMixerState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.completeCurrentBuffer();

        blackhole.consume(res);
    }
}
