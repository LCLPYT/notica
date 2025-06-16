package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static work.lclpnet.notica.util.TestUtil.getBufferByteSize;
import static work.lclpnet.notica.util.TestUtil.getFrames;

public class BatchSongMixerTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", BatchSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        float seconds = 8;
        int bufferSize = getBufferByteSize(seconds);
        int frames = getFrames(bufferSize);

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, bufferSize, sampleManager, CatmullRomNoteSampler::new);
        BatchSongMixer songMixer = new BatchSongMixer(soundMixer, song);

        songMixer.setSongVolume(0.5f);

        float lookAheadSeconds = soundMixer.getCompressorLookAheadSeconds();

        int startTick = song.tempo().durationTicks(0, 0);
        int endTick = startTick + song.tempo().durationTicks(startTick, seconds + lookAheadSeconds);

        songMixer.mixTicks(startTick, endTick, 0);

        ByteBuffer buffer = soundMixer.applyCompressor(frames, soundMixer.getRootScope());

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(buffer);

        System.out.println("File exported to " + path.toAbsolutePath());

        if (!OPEN) return;

        TestUtil.openFile(path);
    }
}
