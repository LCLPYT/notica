package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SongExporterTest {

    private static final boolean OPEN = true;

    @Test
    public void test() throws IOException {
        String songName = "Driftveil City.nbs";
        Song song = TestUtil.loadSong(songName, SimpleSongMixerTest.class);

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        int workerCount = Runtime.getRuntime().availableProcessors();
        var exporter = new SongExporter(sampleManager, CatmullRomNoteSampler::new, TestUtil.AUDIO_FORMAT, workerCount);

        Path path = Files.createTempFile(songName, ".wav");

        exporter.export(song, 0.5f, StereoMode.SPATIAL, path);

        if (OPEN) {
            TestUtil.openFile(path);
        }
    }
}
