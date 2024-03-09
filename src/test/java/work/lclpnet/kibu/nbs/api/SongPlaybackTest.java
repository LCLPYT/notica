package work.lclpnet.kibu.nbs.api;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.nbs.data.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongPlaybackTest {

    @Test
    void run_test_timingsConsistent() {
        Map<Integer, Note> notes = new HashMap<>(10);
        Note note = new Note((byte) 0, (byte) 1, (byte) 100, (short) 100, (short) 100);
        notes.put(0, note);
        notes.put(1, note);
        notes.put(2, note);
        notes.put(3, note);
        notes.put(4, note);

        int ticks = 5;
        Song song = new Song(ticks, 16f, SongMeta.EMPTY, LoopConfig.NONE, Map.of(0, Layer.of(notes)),
                Instruments.DEFAULT, false, (byte) 4);

        LongList timestamps = new LongArrayList(ticks);
        NotePlayer player = (s, l, n) -> timestamps.add(System.currentTimeMillis());

        var playback = new SongPlayback(song, player);

        playback.run();  // execute on this thread (as opposed to playback.start())

        assertEquals(ticks, timestamps.size());
        long last = timestamps.getLong(0);

        for (int i = 1; i < ticks; i++) {
            long next = timestamps.getLong(i);
            long diff = next - last;
            assertEquals(63, diff, 5);
            last = next;
        }
    }
    @Test
    void whenDone_afterPlayback_isCalled() {
        Song song = new Song(0, 16f, SongMeta.EMPTY, LoopConfig.NONE,
                Map.of(0, Layer.of(Map.of())), Instruments.DEFAULT, false, (byte) 4);

        var executed = new AtomicBoolean(false);
        var playback = new SongPlayback(song, (s, l, n) -> {});
        playback.whenDone(() -> executed.set(true));

        playback.run();  // execute on this thread (as opposed to playback.start())

        assertTrue(executed.get());
    }
}