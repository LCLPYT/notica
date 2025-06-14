package work.lclpnet.notica.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.infra.Blackhole;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.benchmark.impl.BaselineNoteSampler;
import work.lclpnet.notica.benchmark.impl.CRBaselineNoteSampler;
import work.lclpnet.notica.benchmark.impl.LerpNoteSampler;
import work.lclpnet.notica.impl.mix.*;
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
        SimpleSongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferByteSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, BaselineNoteSampler::new);
            songMixer = new SimpleSongMixer(soundMixer, song);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class LerpSimdState {

        SoundMixer soundMixer;
        SimpleSongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferByteSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, LerpNoteSampler::new);
            songMixer = new SimpleSongMixer(soundMixer, song);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class BaselineCatmullRomState {

        SoundMixer soundMixer;
        SimpleSongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments());
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferByteSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CRBaselineNoteSampler::new);
            songMixer = new SimpleSongMixer(soundMixer, song);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class SimdCatmullRomState {

        SoundMixer soundMixer;
        SimpleSongMixer songMixer;
        int endTick, frames;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerBenchmark.class);

            TestUtil.initSoundRegistry();

            SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
            sampleManager.loadAll();

            float seconds = 5.f;
            int bufferSize = TestUtil.getBufferByteSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new);
            songMixer = new SimpleSongMixer(soundMixer, song);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class BatchCRState {

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
            int bufferSize = TestUtil.getBufferByteSize(seconds);
            frames = getFrames(bufferSize);

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new);
            songMixer = new BatchSongMixer(soundMixer, song);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

    @State(Scope.Thread)
    public static class ParallelBatchCRState {

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
            int bufferSize = TestUtil.getBufferByteSize(seconds);
            frames = getFrames(bufferSize);

            int workerCount = Runtime.getRuntime().availableProcessors();

            soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new, workerCount);
            songMixer = new ParallelBatchSongMixer(soundMixer, song, workerCount);

            endTick = song.tempo().durationTicks(0, seconds);
        }
    }

//    @Benchmark
    public void lerpBaseline(BaselineState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames, state.soundMixer.getRootScope());

        blackhole.consume(res);
    }

//    @Benchmark
    public void lerpSimd(LerpSimdState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames, state.soundMixer.getRootScope());

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomBaseline(BaselineCatmullRomState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames, state.soundMixer.getRootScope());

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomSimd(SimdCatmullRomState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames, state.soundMixer.getRootScope());

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomBatched(BatchCRState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames, state.soundMixer.getRootScope());

        blackhole.consume(res);
    }

    @Benchmark
    public void catmullRomBatchedParallel(ParallelBatchCRState state, Blackhole blackhole) {
        state.songMixer.mixTicks(0, state.endTick, 0);

        ByteBuffer res = state.soundMixer.applyCompressor(state.frames, state.soundMixer.getRootScope());

        blackhole.consume(res);
    }
}
