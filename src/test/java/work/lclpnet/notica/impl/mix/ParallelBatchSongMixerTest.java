package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static work.lclpnet.notica.util.TestUtil.getBufferByteSize;
import static work.lclpnet.notica.util.TestUtil.getFrames;

public class ParallelBatchSongMixerTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", ParallelBatchSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 32;
        int bufferSize = getBufferByteSize(seconds);
        int frames = getFrames(bufferSize);

        int workerCount = 4;
        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new, workerCount);
        var songMixer = new ParallelBatchSongMixer(soundMixer, song, workerCount);

        songMixer.setSongVolume(0.5f);

        float lookAheadSeconds = soundMixer.getCompressorLookAheadSeconds();

        int startTick = song.tempo().durationTicks(0, 0);
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds + lookAheadSeconds);

        songMixer.mixTicks(startTick, endTick, 0);

        ByteBuffer buffer = soundMixer.applyCompressor(frames, soundMixer.getScope());

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(buffer);

        System.out.println("File exported to " + path.toAbsolutePath());

        if (!OPEN) return;

        TestUtil.openFile(path);
    }

    @Test
    void testContinuousSameAsBatchSongMixer() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", ParallelBatchSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 32;
        int bufferSize = getBufferByteSize(seconds);
        int frames = getFrames(bufferSize);
        float songVolume = 0.5f;

        ByteBuffer sequential = sequentialSample(song, bufferSize, sampleManager, songVolume, seconds, frames);
        ByteBuffer parallel = parallelSample(song, bufferSize, sampleManager, songVolume, seconds, frames);

        float[] sequential_array = TestUtil.toFloatArray(TestUtil.asShortArray(sequential));
        float[] parallel_array = TestUtil.toFloatArray(TestUtil.asShortArray(parallel));

        assertArrayEquals(sequential_array, parallel_array, "Parallel does not match sequential (buffer_size=%s)"
                .formatted(bufferSize / 2));
    }

    @Test
    void testCombinedSameAsBatchSongMixer() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", ParallelBatchSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        int chunks = 32;
        float chunkSeconds = 1;
        int bufferSize = getBufferByteSize(chunkSeconds);
        int frames = getFrames(bufferSize);
        float songVolume = 0.5f;

        ByteBuffer sequential = sequentialChunked(song, bufferSize, sampleManager, songVolume, chunkSeconds, frames, chunks);
        ByteBuffer parallel = parallelChunked(song, bufferSize, sampleManager, songVolume, chunkSeconds, frames, chunks);

        float[] sequential_array = TestUtil.toFloatArray(TestUtil.asShortArray(sequential));
        float[] parallel_array = TestUtil.toFloatArray(TestUtil.asShortArray(parallel));

        assertArrayEquals(sequential_array, parallel_array, "Parallel does not match sequential (buffer_size=%s)"
                .formatted(bufferSize / 2));

        if (!EXPORT) return;

        sequential.flip();
        parallel.flip();

        Path seqPath = TestUtil.exportSound(sequential);
        Path parPath = TestUtil.exportSound(parallel);

        System.out.println("Sequential exported to " + seqPath.toAbsolutePath());
        System.out.println("Parallel exported to " + parPath.toAbsolutePath());

        if (!OPEN) return;

        TestUtil.openFile(seqPath.getParent());
    }

    private ByteBuffer sequentialSample(Song song, int bufferSize, SoundSampleManager sampleManager, float songVolume, float seconds, int frames) throws IOException {
        int workerCount = 1;
        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new, workerCount);
        var songMixer = new BatchSongMixer(soundMixer, song);

        songMixer.setSongVolume(songVolume);

        float lookAheadSeconds = soundMixer.getCompressorLookAheadSeconds();

        int startTick = song.tempo().durationTicks(0, 0);
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds + lookAheadSeconds);

        songMixer.mixTicks(startTick, endTick, 0);

        return soundMixer.applyCompressor(frames, soundMixer.getScope());
    }

    private ByteBuffer parallelSample(Song song, int bufferSize, SoundSampleManager sampleManager, float songVolume, float seconds, int frames) throws IOException {
        int workerCount = 4;
        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new, workerCount);
        var songMixer = new ParallelBatchSongMixer(soundMixer, song, workerCount);

        songMixer.setSongVolume(songVolume);

        float lookAheadSeconds = soundMixer.getCompressorLookAheadSeconds();

        int startTick = song.tempo().durationTicks(0, 0);
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds + lookAheadSeconds);

        songMixer.mixTicks(startTick, endTick, 0);

        return soundMixer.applyCompressor(frames, soundMixer.getScope());
    }

    private ByteBuffer sequentialChunked(Song song, int bufferSize, SoundSampleManager sampleManager, float songVolume,
                                           float seconds, int frames, int chunkCount) throws IOException {
        int workerCount = 1;
        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new, workerCount);
        var songMixer = new BatchSongMixer(soundMixer, song);

        songMixer.setSongVolume(songVolume);

        ByteBuffer combined = BufferUtils.createByteBuffer(chunkCount * bufferSize);
        int startTick = 0;

        for (int i = 0; i < chunkCount; i++) {
            float timeSeconds = seconds;

            if (i == 0) {
                timeSeconds += soundMixer.getCompressorLookAheadSeconds();
            }

            int endTick = startTick + song.tempo().durationTicks(startTick, timeSeconds);

            songMixer.mixTicks(startTick, endTick, 0);

            ByteBuffer buf = soundMixer.applyCompressor(frames, soundMixer.getScope());
            combined.put(buf);

            soundMixer.advanceBuffer();

            startTick = endTick;
        }

        combined.flip();

        return combined;
    }

    private ByteBuffer parallelChunked(Song song, int bufferSize, SoundSampleManager sampleManager, float songVolume,
                                         float seconds, int frames, int chunkCount) throws IOException {
        int workerCount = 4;
        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new, workerCount);
        var songMixer = new ParallelBatchSongMixer(soundMixer, song, workerCount);

        songMixer.setSongVolume(songVolume);

        ByteBuffer combined = BufferUtils.createByteBuffer(chunkCount * bufferSize);
        int startTick = 0;

        for (int i = 0; i < chunkCount; i++) {
            float timeSeconds = seconds;

            if (i == 0) {
                timeSeconds += soundMixer.getCompressorLookAheadSeconds();
            }

            int endTick = startTick + song.tempo().durationTicks(startTick, timeSeconds);

            songMixer.mixTicks(startTick, endTick, 0);

            ByteBuffer buf = soundMixer.applyCompressor(frames, soundMixer.getScope());
            combined.put(buf);

            soundMixer.advanceBuffer();

            startTick = endTick;
        }

        combined.flip();

        return combined;
    }
}
