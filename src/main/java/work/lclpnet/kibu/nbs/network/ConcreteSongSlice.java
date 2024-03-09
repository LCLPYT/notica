package work.lclpnet.kibu.nbs.network;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.NoteEvent;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.NoteContainer;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.impl.MutableNoteEvent;

import java.util.Iterator;

public record ConcreteSongSlice(Index<? extends NoteContainer> layers, int tickStart, int tickEnd, int layerStart, int layerEnd) implements SongSlice {

    public ConcreteSongSlice(Song song, int tickStart, int tickEnd, int layerStart, int layerEnd) {
        this(song.layers(), tickStart, tickEnd, layerStart, layerEnd);
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
                        NoteContainer layer = layers.get(layerIndex);

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
