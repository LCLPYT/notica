package work.lclpnet.kibu.nbs.api.data;

import work.lclpnet.kibu.nbs.api.Index;

public interface Layer extends LayerInfo {

    /**
     * @return The name of the layer.
     */
    String name();

    /**
     * @return The notes of this layer, by song tick.
     */
    Index<? extends Note> notes();
}
