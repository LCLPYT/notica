package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static work.lclpnet.notica.util.TestUtil.getBufferByteSize;
import static work.lclpnet.notica.util.TestUtil.getFrames;

class SongAudioStreamTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 3;
        int bufferBytes = getBufferByteSize(seconds);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        songMixer.setSongVolume(0.5f);

        @SuppressWarnings("resource")
        var stream = new SongAudioStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song, bufferBytes);

        Path dir;

        if (EXPORT) {
            dir = Files.createTempDirectory("notica_test");

            System.out.println("Exporting into " + dir.toAbsolutePath());
        } else {
            dir = null;
        }

        byte[][] parts = new byte[amount][0];

        for (int i = 0; i < amount; i++) {
            ByteBuffer buf = stream.read(bufferBytes);

            if (buf == null) break;

            parts[i] = asByteArray(buf);

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

        Runtime.getRuntime().exec(new String[] {"xdg-open", dir.toAbsolutePath().toString()});
    }

    private byte[] asByteArray(ByteBuffer buf) {
        byte[] array = new byte[buf.limit()];

        for (int j = 0; j < array.length; j++) {
            array[j] = buf.get();
        }

        return array;
    }

    private short[] asShortArray(ByteBuffer buf) {
        short[] array = new short[buf.limit() / 2];

        for (int j = 0; j < array.length; j++) {
            array[j] = buf.getShort();
        }

        return array;
    }

    private float[] toFloatArray(short[] samples) {
        float[] array = new float[samples.length];

        for (int i = 0; i < array.length; i++) {
            array[i] = i / (float) Short.MAX_VALUE;
        }

        return array;
    }

    @Test
    void testSameAsContinuous() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 5;
        float volume = 1.5f;

        ByteBuffer reference = reference(song, volume, sampleManager, seconds, amount);
        ByteBuffer combined = combined(song, volume, sampleManager, seconds, amount);

        float[] reference_array = toFloatArray(asShortArray(reference));
        float[] combined_array = toFloatArray(asShortArray(combined));

        assertArrayEquals(reference_array, combined_array, "Combined does not match reference (buffer_size=%s)"
                .formatted(getBufferByteSize(seconds) / 2));
    }

    private ByteBuffer combined(Song song, float volume, SoundSampleManager sampleManager, float seconds, int amount) throws IOException {
        int bufferBytes = getBufferByteSize(seconds);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        songMixer.setSongVolume(volume);

        @SuppressWarnings("resource")
        var stream = new SongAudioStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song, bufferBytes);

        ByteBuffer combined = BufferUtils.createByteBuffer(bufferBytes * amount);

        for (int i = 0; i < amount; i++) {
            ByteBuffer buf = stream.read(bufferBytes);

            if (buf == null) break;

            combined.put(buf);
        }

        combined.flip();

        return combined;
    }

    private ByteBuffer reference(Song song, float volume, SoundSampleManager sampleManager, float seconds, int amount) throws IOException {
        int bufferBytes = getBufferByteSize(seconds) * amount;
        int frames = getFrames(bufferBytes);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new);
        SimpleSongMixer songMixer = new SimpleSongMixer(soundMixer, song);

        int startTick = 0;
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds * amount);

        songMixer.setSongVolume(volume);
        songMixer.mixTicks(startTick, endTick, 0);

        return soundMixer.applyCompressor(frames, soundMixer.getScope());
    }
}