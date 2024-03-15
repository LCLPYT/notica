package work.lclpnet.notica.impl;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.network.ConcreteSongSlice;
import work.lclpnet.notica.network.SongHeader;
import work.lclpnet.notica.test.TestSongHelper;

import static org.junit.jupiter.api.Assertions.*;

class PendingSongTest {

    @Test
    void accept() {
        Song song = TestSongHelper.createSong();
        SongSlice initial = new ConcreteSongSlice(song, 0, 10, 0, 2);
        SongSlice rest = new ConcreteSongSlice(song, 10, 25, 3, 0);
        SongHeader header = new SongHeader(song);

        PendingSong pending = new PendingSong(header);
        pending.accept(initial);

        assertEquals(3, pending.layers().size());

        MutableLayer layer0 = pending.layers().get(0);
        MutableLayer layer1 = pending.layers().get(1);
        MutableLayer layer2 = pending.layers().get(2);

        assertNotNull(layer0);
        assertNotNull(layer1);
        assertNotNull(layer2);

        assertArrayEquals(new int[] {}, layer0.notes().streamKeysOrdered().toArray());
        assertArrayEquals(new int[] {0}, layer1.notes().streamKeysOrdered().toArray());
        assertArrayEquals(new int[] {0, 10}, layer2.notes().streamKeysOrdered().toArray());

        pending.accept(rest);

        assertArrayEquals(new int[] {14, 25}, layer0.notes().streamKeysOrdered().toArray());
        assertArrayEquals(new int[] {0, 14}, layer1.notes().streamKeysOrdered().toArray());
        assertArrayEquals(new int[] {0, 10, 14}, layer2.notes().streamKeysOrdered().toArray());
    }
}