package work.lclpnet.kibu.nbs.util;

import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.*;
import work.lclpnet.kibu.nbs.impl.ListIndex;
import work.lclpnet.kibu.nbs.impl.data.ImmutableSongMeta;
import work.lclpnet.kibu.nbs.network.SongHeader;

import java.util.HashMap;

public class PendingSong implements Song {

    private final int durationTicks;
    private final float ticksPerSecond;
    private final LoopConfig loopConfig;
    private final Index<PendingLayer> layers;
    private final Instruments instruments;
    private final boolean stereo;
    private final byte signature;

    public PendingSong(SongHeader header) {
        this.durationTicks = header.durationTicks();
        this.ticksPerSecond = header.ticksPerSecond();
        this.loopConfig = header.loopConfig();
        this.instruments = header.instruments();
        this.stereo = header.stereo();
        this.signature = header.signature();

        var layerInfo = header.layerInfo();
        var layers = new HashMap<Integer, PendingLayer>(layerInfo.size());

        for (var entry : layerInfo.iterate()) {
            LayerInfo info = entry.value();

            PendingLayer layer = new PendingLayer(info.volume(), info.panning());

            layers.put(entry.index(), layer);
        }

        this.layers = new ListIndex<>(layers);
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
    public Index<PendingLayer> layers() {
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
     * @return True, if the there is more data to fetch. False if the pending song has been completely loaded.
     */
    public boolean accept(SongSlice slice) {
        // TODO
        return true;
    }
}
