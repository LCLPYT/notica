package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.benchmark.impl.CRBaselineNoteSampler;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.impl.mix.CatmullRomNoteSampler;
import work.lclpnet.notica.impl.mix.SongMixer;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static work.lclpnet.notica.util.TestUtil.getFrames;

@SuppressWarnings({"FieldMayBeFinal", "DuplicatedCode"})
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
public class SongMixerBenchmark {

    @State(Scope.Thread)
    public static class BaselineState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CRBaselineNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class SplitVolumeState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CRBaselineNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class BaselineCatmullRomState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CRBaselineNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class SimdCatmullRomState {

        SoundMixer soundMixer;
        SongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CRBaselineNoteSampler::new);
            songMixer = TestUtil.createSongMixer(song, soundMixer);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @Benchmark
    public void baseline(BaselineState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames);

        blackhole.consume(res);
    }

    @Benchmark
    public void splitVolume(SplitVolumeState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames);

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomBaseline(BaselineCatmullRomState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames);

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomSimd(SimdCatmullRomState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames);

        blackhole.consume(res);
    }
}
