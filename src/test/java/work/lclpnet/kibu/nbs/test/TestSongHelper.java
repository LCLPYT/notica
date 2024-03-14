package work.lclpnet.kibu.nbs.test;

import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.FixedIndex;
import work.lclpnet.kibu.nbs.impl.data.*;

import java.util.Map;

import static work.lclpnet.kibu.nbs.impl.data.ImmutableNote.of;

public class TestSongHelper {

    public static Song createSongWithoutLayers() {
        Index<Layer> layers = new FixedIndex<>(Map.of());

        return new ImmutableSong(25, 5f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                layers, ImmutableInstruments.DEFAULT, false, (byte) 4);
    }

    public static Song createSongWithoutNotes() {
        Index<Layer> layers = new FixedIndex<>(Map.of(
                1, ImmutableLayer.of(new FixedIndex<>(Map.of())),
                0, ImmutableLayer.of(new FixedIndex<>(Map.of()))));

        return new ImmutableSong(25, 5f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                layers, ImmutableInstruments.DEFAULT, false, (byte) 4);
    }

    public static Song createSong() {
        Map<Integer, Note> a = Map.of(0, of(1, 45), 14, of(3, 46));
        Map<Integer, Note> b = Map.of(25, of(3, 41), 14, of(5, 40));
        Map<Integer, Note> c = Map.of(0, of(2, 49), 10, of(0, 35), 14, of(4, 39));

        Index<Layer> layers = new FixedIndex<>(Map.of(
                1, ImmutableLayer.of(new FixedIndex<>(a)),
                0, ImmutableLayer.of(new FixedIndex<>(b)),
                2, ImmutableLayer.of(new FixedIndex<>(c))));

        return new ImmutableSong(25, 5f, ImmutableSongMeta.EMPTY, ImmutableLoopConfig.NONE,
                layers, ImmutableInstruments.DEFAULT, false, (byte) 4);
    }
}
