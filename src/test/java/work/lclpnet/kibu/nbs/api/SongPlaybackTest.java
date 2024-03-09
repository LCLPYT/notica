package work.lclpnet.kibu.nbs.api;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.impl.data.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongPlaybackTest {

    @Test
    void run_test_timingsConsistent() throws ReflectiveOperationException {
        Map<Integer, Note> notes = new HashMap<>(10);
        ImmutableNote note = new ImmutableNote((byte) 0, (byte) 1, (byte) 100, (short) 100, (short) 100);
        notes.put(0, note);
        notes.put(1, note);
        notes.put(2, note);
        notes.put(3, note);
        notes.put(4, note);

        int ticks = 5;
        ImmutableSong song = new ImmutableSong(ticks, 16f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE, Map.of(0, ImmutableLayer.of(notes)),
                ImmutableInstruments.DEFAULT, false, (byte) 4);

        LongList timestamps = new LongArrayList(ticks);
        NotePlayer player = (s, l, n) -> timestamps.add(System.currentTimeMillis());

        var playback = new SongPlayback(song, player);
        setStarted(playback);

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

    private static void setStarted(SongPlayback playback) throws ReflectiveOperationException {
        Field started = SongPlayback.class.getDeclaredField("started");
        started.setAccessible(true);
        started.set(playback, true);
    }

    @Test
    void whenDone_afterPlayback_isCalled() {
        ImmutableSong song = new ImmutableSong(0, 16f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                Map.of(0, ImmutableLayer.of(Map.of())), ImmutableInstruments.DEFAULT, false, (byte) 4);

        var executed = new AtomicBoolean(false);
        var playback = new SongPlayback(song, (s, l, n) -> {});
        playback.whenDone(() -> executed.set(true));

        playback.run();  // execute on this thread (as opposed to playback.start())

        assertTrue(executed.get());
    }
}