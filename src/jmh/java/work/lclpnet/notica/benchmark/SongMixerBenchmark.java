package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.benchmark.impl.CRBaselineNoteSampler;
import work.lclpnet.notica.benchmark.impl.CROptimizedNoteSampler;
import work.lclpnet.notica.benchmark.impl.SplitNoteSampler;
import work.lclpnet.notica.benchmark.impl.SplitVolumeNoteSampler;
import work.lclpnet.notica.impl.BaselineNoteSampler;
import work.lclpnet.notica.impl.SoundSampleManager;
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

    @State(Scope.Thread)
    public static class BaselineState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            soundMixer = TestUtil.createSoundMixer(song, sampleManager, BaselineNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, 5);
        }
    }

    @State(Scope.Thread)
    public static class SplitState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            soundMixer = TestUtil.createSoundMixer(song, sampleManager, SplitNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, 5);
        }
    }

    @State(Scope.Thread)
    public static class SplitVolumeState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            soundMixer = TestUtil.createSoundMixer(song, sampleManager, SplitVolumeNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, 5);
        }
    }

    @State(Scope.Thread)
    public static class CatmullRomBaselineState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            soundMixer = TestUtil.createSoundMixer(song, sampleManager, CRBaselineNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, 5);
        }
    }

    @State(Scope.Thread)
    public static class CatmullRomOptimizedState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            soundMixer = TestUtil.createSoundMixer(song, sampleManager, CROptimizedNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, 5);
        }
    }

    @Benchmark
    public void baseline(BaselineState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.completeCurrentBuffer();

        blackhole.consume(res);
    }

//    @Benchmark
    public void split(SplitState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.completeCurrentBuffer();

        blackhole.consume(res);
    }

    @Benchmark
    public void splitVolume(SplitVolumeState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.completeCurrentBuffer();

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomBaseline(CatmullRomBaselineState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.completeCurrentBuffer();

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomOptimized(CatmullRomOptimizedState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.completeCurrentBuffer();

        blackhole.consume(res);
    }
}
