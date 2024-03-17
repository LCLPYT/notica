package work.lclpnet.notica.impl;

import work.lclpnet.notica.api.Index;
import work.lclpnet.notica.api.NoteEvent;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.api.data.*;
import work.lclpnet.notica.impl.data.ImmutableSongMeta;
import work.lclpnet.notica.network.SongHeader;

import java.util.HashMap;

public class PendingSong implements Song {

    private final int durationTicks;
    private final float ticksPerSecond;
    private final LoopConfig loopConfig;
    private final Index<MutableLayer> layers;
    private final Instruments instruments;
    private final boolean stereo;
    private final byte signature;
    private int startTick;

    public PendingSong(SongHeader header) {
        this(header, 0);
    }

    public PendingSong(SongHeader header, int startTick) {
        this.durationTicks = header.durationTicks();
        this.ticksPerSecond = header.ticksPerSecond();
        this.loopConfig = header.loopConfig();
        this.instruments = header.instruments();
        this.stereo = header.stereo();
        this.signature = header.signature();
        this.startTick = startTick;

        var layerInfo = header.layerInfo();
        var layers = new HashMap<Integer, MutableLayer>(layerInfo.size());

        for (var entry : layerInfo.iterateOrdered()) {
            LayerInfo info = entry.value();

            MutableLayer layer = new MutableLayer(info.volume(), info.panning());

            layers.put(entry.index(), layer);
        }

        this.layers = new FixedIndex<>(layers);
    }

    @Override
    public int durationTicks() {
        return durationTicks;
    }

    @Override
    public float ticksPerSecond() {
        return ticksPerSecond;
    }

    @Override
    public SongMeta metaData() {
        return ImmutableSongMeta.EMPTY;
    }

    @Override
    public LoopConfig loopConfig() {
        return loopConfig;
    }

    @Override
    public Index<MutableLayer> layers() {
        return layers;
    }

    @Override
    public Instruments instruments() {
        return instruments;
    }

    @Override
    public boolean stereo() {
        return stereo;
    }

    @Override
    public byte signature() {
        return signature;
    }

    /**
     * Load a song slice into this song.
     * @param slice The song slice.
     */
    public void accept(SongSlice slice) {
        startTick = Math.max(0, Math.min(slice.tickStart(), startTick));

        for (NoteEvent noteEvent : slice) {
            MutableLayer layer = layers.get(noteEvent.layer());

            if (layer == null) continue;

            layer.accept(noteEvent);
        }
    }

    public int getStartTick() {
        return startTick;
    }
}
