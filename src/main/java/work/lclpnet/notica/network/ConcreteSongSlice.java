package work.lclpnet.notica.network;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.notica.api.Index;
import work.lclpnet.notica.api.NoteEvent;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.NoteContainer;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.MutableNoteEvent;

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
        int minLayerIndex = layers.streamKeysOrdered().min().orElse(0);
        int maxLayerIndex = layers.streamKeysOrdered().max().orElse(-1);

        return new Iterator<>() {
            final MutableNoteEvent noteEvent = new MutableNoteEvent();
            int tick = tickStart;
            int layerIndex = Math.max(minLayerIndex, layerStart);
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
                        untilLayer = layerEnd;
                    } else {
                        untilLayer = maxLayerIndex;
                    }

                    for (; layerIndex <= untilLayer; layerIndex++) {
                        NoteContainer layer = layers.get(layerIndex);

                        if (layer == null) continue;

                        Note note = layer.notes().get(tick);

                        if (note == null) continue;

                        noteEvent.set(tick, layerIndex, note);
                        hasNext = true;
                        layerIndex++;
                        return;
                    }

                    layerIndex = minLayerIndex;
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
