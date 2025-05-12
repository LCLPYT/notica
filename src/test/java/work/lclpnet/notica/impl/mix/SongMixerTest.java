package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SongMixerTest {

    private static final boolean EXPORT = false;

    @Test
    void test() throws IOException {
        Song song = TestUtil.loadSong("Driftveil City.nbs", getClass());
        SoundMixer soundMixer = TestUtil.createSoundMixer(song);
        SongMixer songMixer = TestUtil.createSongMixer(song, soundMixer);

        soundMixer.preloadSounds().join();

        int startTick = 0;
        int endTick = song.tempo().durationTicks(startTick, 5);

        songMixer.mixTicks(startTick, endTick, 0);

        ByteBuffer buffer = soundMixer.completeCurrentBuffer();

        if (EXPORT) {
            System.out.println("File exported to " + TestUtil.exportSound(buffer));
        }
    }
}
