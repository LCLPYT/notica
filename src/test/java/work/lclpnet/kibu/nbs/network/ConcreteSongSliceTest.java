package work.lclpnet.kibu.nbs.network;

import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.NoteEvent;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.ListIndex;
import work.lclpnet.kibu.nbs.impl.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static work.lclpnet.kibu.nbs.impl.data.ImmutableNote.of;

class ConcreteSongSliceTest {

    @Test
    void iterator_noLayers_none() {
        ConcreteSongSlice slice = new ConcreteSongSlice(createSongWithoutLayers(), 0, 25, 0, 2);
        var it = slice.iterator();

        assertFalse(it.hasNext());
    }

    @Test
    void iterator_withoutNotes_none() {
        ConcreteSongSlice slice = new ConcreteSongSlice(createSongWithoutNotes(), 0, 25, 0, 2);
        var it = slice.iterator();

        assertFalse(it.hasNext());
    }

    @Test
    void iterator_fullSong_asExpected() {
        ConcreteSongSlice slice = new ConcreteSongSlice(createSong(), 0, 25, 0, 2);
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
        ConcreteSongSlice slice = new ConcreteSongSlice(createSong(), 0, 14, 2, 1);
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
        ConcreteSongSlice slice = new ConcreteSongSlice(createSong(), -5, 31, -10, 20);
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

    public static Song createSongWithoutLayers() {
        Index<Layer> layers = new ListIndex<>(Map.of());

        return new ImmutableSong(25, 5f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                layers, ImmutableInstruments.DEFAULT, false, (byte) 4);
    }

    public static Song createSongWithoutNotes() {
        Index<Layer> layers = new ListIndex<>(Map.of(
                1, ImmutableLayer.of(new ListIndex<>(Map.of())),
                0, ImmutableLayer.of(new ListIndex<>(Map.of()))));

        return new ImmutableSong(25, 5f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                layers, ImmutableInstruments.DEFAULT, false, (byte) 4);
    }

    public static Song createSong() {
        Map<Integer, Note> a = Map.of(0, of(1, 45), 14, of(3, 46));
        Map<Integer, Note> b = Map.of(25, of(3, 41), 14, of(5, 40));
        Map<Integer, Note> c = Map.of(0, of(2, 49), 10, of(0, 35), 14, of(4, 39));

        Index<Layer> layers = new ListIndex<>(Map.of(
                1, ImmutableLayer.of(new ListIndex<>(a)),
                0, ImmutableLayer.of(new ListIndex<>(b)),
                2, ImmutableLayer.of(new ListIndex<>(c))));

        return new ImmutableSong(25, 5f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                layers, ImmutableInstruments.DEFAULT, false, (byte) 4);
    }
}