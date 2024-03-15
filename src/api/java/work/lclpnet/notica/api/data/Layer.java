package work.lclpnet.notica.api.data;

public interface Layer extends LayerInfo, NoteContainer {

    /**
     * @return The name of the layer.
     */
    String name();
}
