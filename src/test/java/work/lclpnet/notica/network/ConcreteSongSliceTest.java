package work.lclpnet.notica.network;

import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.NoteEvent;
import work.lclpnet.notica.test.TestSongHelper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConcreteSongSliceTest {

    @Test
    void iterator_noLayers_none() {
        ConcreteSongSlice slice = new ConcreteSongSlice(TestSongHelper.createSongWithoutLayers(), 0, 25, 0, 2);
        var it = slice.iterator();

        assertFalse(it.hasNext());
    }

    @Test
    void iterator_withoutNotes_none() {
        ConcreteSongSlice slice = new ConcreteSongSlice(TestSongHelper.createSongWithoutNotes(), 0, 25, 0, 2);
        var it = slice.iterator();

        assertFalse(it.hasNext());
    }

    @Test
    void iterator_fullSong_asExpected() {
        ConcreteSongSlice slice = new ConcreteSongSlice(TestSongHelper.createSong(), 0, 25, 0, 2);
        var it = slice.iterator();

        List<String> events = new ArrayList<>();

        while (it.hasNext()) {
            NoteEvent next = it.next();
            events.add("%d,%d,(%s,%s)".formatted(next.tick(), next.layer(), next.note().instrument(), next.note().key()));
        }

        assertEquals('[' + String.join(", ",
                "0,1,(1,45)", "0,2,(2,49)", "10,2,(0,35)", "14,0,(5,40)", "14,1,(3,46)", "14,2,(4,39)", "25,0,(3,41)"
                ) + ']', events.toString());
    }

    @Test
    void iterator_slice_asExpected() {
        ConcreteSongSlice slice = new ConcreteSongSlice(TestSongHelper.createSong(), 0, 14, 2, 1);
        var it = slice.iterator();

        List<String> events = new ArrayList<>();

        while (it.hasNext()) {
            NoteEvent next = it.next();
            events.add("%d,%d,(%s,%s)".formatted(next.tick(), next.layer(), next.note().instrument(), next.note().key()));
        }

        assertEquals('[' + String.join(", ",
                "0,2,(2,49)", "10,2,(0,35)", "14,0,(5,40)", "14,1,(3,46)"
        ) + ']', events.toString());
    }

    @Test
    void iterator_outOfBounds_asExpected() {
        ConcreteSongSlice slice = new ConcreteSongSlice(TestSongHelper.createSong(), -5, 31, -10, 20);
        var it = slice.iterator();

        List<String> events = new ArrayList<>();

        while (it.hasNext()) {
            NoteEvent next = it.next();
            events.add("%d,%d,(%s,%s)".formatted(next.tick(), next.layer(), next.note().instrument(), next.note().key()));
        }

        assertEquals('[' + String.join(", ",
                "0,1,(1,45)", "0,2,(2,49)", "10,2,(0,35)", "14,0,(5,40)", "14,1,(3,46)", "14,2,(4,39)", "25,0,(3,41)"
        ) + ']', events.toString());
    }

}