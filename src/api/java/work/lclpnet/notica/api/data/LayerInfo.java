package work.lclpnet.notica.api.data;

public interface LayerInfo {

    /**
     * @return The volume of the layer, ranging [0, 100]
     */
    byte volume();

    /**
     * @return Panning of this layer, ranging [0, 200], where 100=center, 0 is 2 blocks right and 200 is 2 blocks left.
     */
    short panning();

    /**
     * @return Whether the layer is marked as locked.
     */
    boolean locked();
}
