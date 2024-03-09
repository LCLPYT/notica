package work.lclpnet.kibu.nbs.network;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.nbs.api.NoteEvent;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.MutableNoteEvent;

import java.util.Iterator;

public class ConcreteSongSlice implements SongSlice {

    private final Song song;
    private final int tickStart, tickEnd;
    private final int layerStart, layerEnd;

    public ConcreteSongSlice(Song song, int tickStart, int tickEnd, int layerStart, int layerEnd) {
        this.song = song;
        this.tickStart = tickStart;
        this.tickEnd = tickEnd;
        this.layerStart = layerStart;
        this.layerEnd = layerEnd;
    }

    @Override
    public int tickStart() {
        return tickStart;
    }

    @Override
    public int tickEnd() {
        return tickEnd;
    }

    @Override
    public int layerStart() {
        return layerStart;
    }

    @Override
    public int layerEnd() {
        return layerEnd;
    }

    @NotNull
    @Override
    public Iterator<NoteEvent> iterator() {
        var layers = song.layers();
        int layerCount = layers.size();
        int firstLayerIndex = layers.streamKeys().min().orElse(0);

        return new Iterator<>() {
            final MutableNoteEvent noteEvent = new MutableNoteEvent();
            int tick = tickStart;
            int layerIndex = layerStart;
            boolean hasNext = false, done = false;

            @Override
            public boolean hasNext() {
                if (!hasNext) {
                    advance();
                }

                return !done;
            }

            private void advance() {
                for (; tick <= tickEnd; tick++) {
                    int untilLayer;

                    if (tick == tickEnd) {
                        untilLayer = layerEnd + 1;
                    } else {
                        untilLayer = layerCount;
                    }

                    for (; layerIndex < untilLayer; layerIndex++) {
                        Layer layer = layers.get(layerIndex);

                        if (layer == null) continue;

                        Note note = layer.notes().get(tick);

                        if (note == null) continue;

                        noteEvent.set(tick, layerIndex, note);
                        hasNext = true;
                        layerIndex++;
                        return;
                    }

                    layerIndex = firstLayerIndex;
                }

                done = true;
            }

            @Override
            public NoteEvent next() {
                hasNext = false;
                return noteEvent;
            }
        };
    }
}
