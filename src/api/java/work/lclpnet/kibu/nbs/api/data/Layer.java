package work.lclpnet.kibu.nbs.api.data;

import java.util.Map;

public interface Layer {

    /**
     * @return The name of the layer.
     */
    String name();

    /**
     * @return The volume of the layer, ranging [0, 100]
     */
    byte volume();

    /**
     * @return Panning of this layer, ranging [0, 200], where 100=center.
     */
    short panning();

    /**
     * @return The notes of this layer, by song tick.
     */
    Map<Integer, Note> notes();
}
