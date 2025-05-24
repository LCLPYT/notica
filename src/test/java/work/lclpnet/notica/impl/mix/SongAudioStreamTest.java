package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static work.lclpnet.notica.util.TestUtil.getBufferSize;

class SongAudioStreamTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong(Path.of("run", "config", "notica", "songs", "Driftveil City.nbs"));

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 1.f;
        int amount = 3;
        int bufferSize = getBufferSize(seconds);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new);
        SongMixer songMixer = TestUtil.createSongMixer(song, soundMixer);

        songMixer.setSongVolume(0.5f);

        @SuppressWarnings("resource")
        var stream = new SongAudioStream(TestUtil.AUDIO_FORMAT, soundMixer, songMixer, song);

        Path dir = Files.createTempDirectory("notica_test");

        System.out.println("Exporting into " + dir.toAbsolutePath());

        byte[][] parts = new byte[amount][0];

        for (int i = 0; i < amount; i++) {
            ByteBuffer buf = stream.read(bufferSize);

            if (buf == null) break;

            byte[] array = new byte[buf.limit()];

            for (int j = 0; j < array.length; j++) {
                array[j] = buf.get();
            }

            parts[i] = array;

            buf.flip();

            if (!EXPORT) continue;

            TestUtil.exportSound(buf, dir.resolve(i + ".wav"));
        }

        if (EXPORT) {
            int totalSize = Arrays.stream(parts).mapToInt(part -> part.length).sum();
            ByteBuffer buf = ByteBuffer.allocate(totalSize);

            for (byte[] part : parts) {
                buf.put(part);
            }

            buf.flip();

            TestUtil.exportSound(buf, dir.resolve("combined.wav"));
        }

        if (OPEN) {
            Runtime.getRuntime().exec(new String[] {"xdg-open", dir.toAbsolutePath().toString()});
        }
    }
}