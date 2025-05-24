package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static work.lclpnet.notica.util.TestUtil.getBufferByteSize;
import static work.lclpnet.notica.util.TestUtil.getFrames;

public class SongMixerTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", SongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 32;
        int bufferSize = getBufferByteSize(seconds);
        int frames = getFrames(bufferSize);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new);
        SongMixer songMixer = TestUtil.createSongMixer(song, soundMixer);

        songMixer.setSongVolume(0.5f);

        float lookAheadSeconds = soundMixer.getCompressorLookAheadSeconds();

        int startTick = song.tempo().durationTicks(0, 3 * 60 + 58 /* 17 * 60 + 25 */);
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds + lookAheadSeconds);

        songMixer.mixTicks(startTick, endTick, 0);

        ByteBuffer buffer = soundMixer.applyCompressor(frames);

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(buffer);

        System.out.println("File exported to " + path.toAbsolutePath());

        if (!OPEN) return;

        Runtime.getRuntime().exec(new String[] {"xdg-open", path.toAbsolutePath().toString()});
    }
}
