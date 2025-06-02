package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SongExporterTest {

    private static final boolean OPEN = false;

    @Test
    public void test() throws IOException {
        String songName = "Driftveil City";
        Song song = TestUtil.loadSong(songName + ".nbs", SimpleSongMixerTest.class);
//        Song song = TestUtil.loadSong(Path.of("run", "config", "notica", "songs", songName + ".nbs"));

        TestUtil.initSoundRegistry();

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        int workerCount = Runtime.getRuntime().availableProcessors();
        var exporter = new SongExporter(sampleManager, CatmullRomNoteSampler::new, TestUtil.AUDIO_FORMAT, workerCount);

        Path path = Files.createTempFile(songName, ".wav");

        exporter.export(song, 0.5f, StereoMode.SPATIAL, path);

        System.out.println("Song exported to " + path.toAbsolutePath());

        if (OPEN) {
            TestUtil.openFile(path);
        }
    }
}
