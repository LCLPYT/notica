package work.lclpnet.kibu.nbs.api.data;

public interface LayerInfo {

    /**
     * @return The volume of the layer, ranging [0, 100]
     */
    byte volume();

    /**
     * @return Panning of this layer, ranging [0, 200], where 100=center.
     */
    short panning();
}
