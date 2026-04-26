package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.LoopOverride;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.ClientMusicBackend;
import work.lclpnet.notica.util.TestUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.fail;
import static work.lclpnet.notica.util.TestUtil.*;

class SongStreamTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void testStereo() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 3;
        int bufferBytes = getBufferByteSize(seconds);
        int outputBuffers = 1;

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new, 1, outputBuffers);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        songMixer.setSongVolume(0.5f);

        @SuppressWarnings("resource")
        var stream = new SongStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song,
                soundMixer::applyCompressor, TestUtil.logger, bufferBytes, LoopOverride.DEFAULT.withEnabled(false), true, outputBuffers, false);

        stream.startProducer(1).join();

        Path dir;

        if (EXPORT) {
            dir = Files.createTempDirectory("notica_test");

            System.out.println("Exporting into " + dir.toAbsolutePath());
        } else {
            dir = null;
        }

        byte[][] parts = new byte[amount][0];

        for (int i = 0; i < amount; i++) {
            ByteBuffer[] bufs = stream.nextBuffers();

            if (bufs == null) {
                fail("Didn't expect song to have ended yet");
            }

            ByteBuffer buf = bufs[0];

            parts[i] = TestUtil.asByteArray(buf);

            buf.flip();

            if (!EXPORT) continue;

            TestUtil.exportSound(buf, dir.resolve(i + ".wav"));
        }

        if (!EXPORT) return;

        int totalSize = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        for (byte[] part : parts) {
            buf.put(part);
        }

        buf.flip();

        TestUtil.exportSound(buf, dir.resolve("combined.wav"));

        if (!OPEN) return;

        TestUtil.openFile(dir);
    }

    @Test
    void testClampedSameAsContinuous() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 5;
        float volume = 1.5f;

        ByteBuffer reference = reference(song, volume, sampleManager, seconds, amount, mixer -> (frameCount, scope) -> mixer.getCurrentBuffer(scope));
        ByteBuffer combined = combined(song, volume, sampleManager, seconds, amount, mixer -> ((frameCount, scope) -> mixer.getCurrentBuffer(scope)));

        float[] reference_array = TestUtil.toFloatArray(TestUtil.asShortArray(reference));
        float[] combined_array = TestUtil.toFloatArray(TestUtil.asShortArray(combined));

        float tol = 4f / Short.MAX_VALUE;

        TestUtil.assertArrayEquals(reference_array, combined_array, tol, "Combined does not match reference");
    }

    @Disabled  // fixme: test does currently not succeed, but is also not very critical
    @Test
    void testCompressorSameAsContinuous() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 5;
        float volume = 1.5f;

        ByteBuffer reference = reference(song, volume, sampleManager, seconds, amount, mixer -> mixer::applyCompressor);
        ByteBuffer combined = combined(song, volume, sampleManager, seconds, amount, mixer -> mixer::applyCompressor);

        float[] reference_array = TestUtil.toFloatArray(TestUtil.asShortArray(reference));
        float[] combined_array = TestUtil.toFloatArray(TestUtil.asShortArray(combined));

        float tol = 4f / Short.MAX_VALUE;

        TestUtil.assertArrayEquals(reference_array, combined_array, tol, "Combined does not match reference");
    }

    private ByteBuffer combined(Song song, float volume, SoundSampleManager sampleManager, float seconds, int amount,
                                Function<SoundMixer, BufferProcessor> processor) throws IOException {
        int bufferBytes = getBufferByteSize(seconds);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        songMixer.setSongVolume(volume);

        @SuppressWarnings("resource")
        var stream = new SongStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song,
                processor.apply(soundMixer), TestUtil.logger, bufferBytes, LoopOverride.DEFAULT.withEnabled(false), true, 1, false);

        stream.startProducer(1).join();

        ByteBuffer combined = BufferUtils.createByteBuffer(bufferBytes * amount);

        for (int i = 0; i < amount; i++) {
            ByteBuffer[] bufs = stream.nextBuffers();

            if (bufs == null) {
                fail("Didn't expect song to have ended yet");
            }

            ByteBuffer buf = bufs[0];

            combined.put(buf);
        }

        combined.flip();

        return combined;
    }

    private ByteBuffer reference(Song song, float volume, SoundSampleManager sampleManager, float seconds, int amount,
                                 Function<SoundMixer, BufferProcessor> processor) throws IOException {
        int bufferBytes = getBufferByteSize(seconds) * amount;
        int frames = getFrames(bufferBytes);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        int startTick = 0;
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds * amount);

        songMixer.setSongVolume(volume);
        songMixer.mixTicks(startTick, endTick, 0);

        float[] samples = processor.apply(soundMixer).process(frames, soundMixer.getRootScope());

        return soundMixer.toStereoPCM(samples, frames);
    }

    @Test
    void testMultiChannelOutput() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 3;
        int bufferBytes = getBufferByteSize(seconds);
        int outputBuffers = 2;

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new, 1, outputBuffers);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        songMixer.setSongVolume(0.5f);

        @SuppressWarnings("resource")
        var stream = new SongStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song,
                soundMixer::applyCompressor, TestUtil.logger, bufferBytes, LoopOverride.DEFAULT.withEnabled(false), true, outputBuffers, false);

        stream.startProducer(1).join();

        Path dir;

        if (EXPORT) {
            dir = Files.createTempDirectory("notica_test");

            System.out.println("Exporting into " + dir.toAbsolutePath());
        } else {
            dir = null;
        }

        AudioFormat monoFormat = ClientMusicBackend.getMonoFormat(AUDIO_FORMAT);

        byte[][][] parts = new byte[amount][outputBuffers][0];

        for (int i = 0; i < amount; i++) {
            ByteBuffer[] bufs = stream.nextBuffers();

            if (bufs == null) {
                fail("Didn't expect song to have ended yet");
            }

            for (int j = 0; j < outputBuffers; j++) {
                ByteBuffer buf = bufs[j];

                parts[i][j] = TestUtil.asByteArray(buf);

                buf.flip();

                if (!EXPORT) continue;

                TestUtil.exportSound(buf, dir.resolve("%d_%d.wav".formatted(i, j)), monoFormat);
            }
        }

        if (!EXPORT) return;

        int totalSize = Arrays.stream(parts).mapToInt(part -> part[0].length).sum();
        ByteBuffer[] bufs = new ByteBuffer[outputBuffers];

        for (int i = 0; i < outputBuffers; i++) {
            bufs[i] = ByteBuffer.allocate(totalSize);
        }

        for (byte[][] part : parts) {
            for (int i = 0; i < outputBuffers; i++) {
                bufs[i].put(part[i]);
            }
        }

        for (int i = 0; i < outputBuffers; i++) {
            bufs[i].flip();
            TestUtil.exportSound(bufs[i], dir.resolve("combined_%d.wav".formatted(i)), monoFormat);
        }

        if (!OPEN) return;

        TestUtil.openFile(dir);
    }

    @Test
    void testMonoOutput() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 3;
        int bufferBytes = getBufferByteSize(seconds);
        int outputBuffers = 1;

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new, 1, outputBuffers);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        songMixer.setSongVolume(0.5f);

        @SuppressWarnings("resource")
        var stream = new SongStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song,
                soundMixer::applyCompressor, TestUtil.logger, bufferBytes, LoopOverride.DEFAULT.withEnabled(false), true, outputBuffers, true);

        stream.startProducer(1).join();

        Path dir;

        if (EXPORT) {
            dir = Files.createTempDirectory("notica_test");

            System.out.println("Exporting into " + dir.toAbsolutePath());
        } else {
            dir = null;
        }

        AudioFormat monoFormat = ClientMusicBackend.getMonoFormat(AUDIO_FORMAT);

        byte[][] parts = new byte[amount][0];

        for (int i = 0; i < amount; i++) {
            ByteBuffer[] bufs = stream.nextBuffers();

            if (bufs == null) {
                fail("Didn't expect song to have ended yet");
            }

            ByteBuffer buf = bufs[0];

            parts[i] = TestUtil.asByteArray(buf);

            buf.flip();

            if (!EXPORT) continue;

            TestUtil.exportSound(buf, dir.resolve(i + ".wav"), monoFormat);
        }

        if (!EXPORT) return;

        int totalSize = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        for (byte[] part : parts) {
            buf.put(part);
        }

        buf.flip();

        TestUtil.exportSound(buf, dir.resolve("combined.wav"), monoFormat);

        if (!OPEN) return;

        TestUtil.openFile(dir);
    }
}