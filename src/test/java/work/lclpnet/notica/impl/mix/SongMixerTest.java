package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class SongMixerTest {

    private static final boolean EXPORT = true, OPEN = true;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong(Path.of("run", "config", "notica", "songs", "Note Block Megacollab.nbs"));

        SoundSampleManager sampleManager = TestUtil.createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);
        sampleManager.loadAll();

        SoundMixer soundMixer = TestUtil.createSoundMixer(song, sampleManager, CatmullRomNoteSampler::new);
        SongMixer songMixer = TestUtil.createSongMixer(song, soundMixer);

        songMixer.setSongVolume(0.5f);

        int startTick = song.tempo().durationTicks(0, 3 * 60 + 58 /* 17 * 60 + 25 */);
        int endTick = startTick + song.tempo().durationTicks(startTick, 5);

        songMixer.mixTicks(startTick, endTick, 0);

        ByteBuffer buffer = soundMixer.completeCurrentBuffer();

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(buffer);

        System.out.println("File exported to " + path.toAbsolutePath());

        if (!OPEN) return;

        Runtime.getRuntime().exec(new String[] {"xdg-open", path.toAbsolutePath().toString()});
    }
}
