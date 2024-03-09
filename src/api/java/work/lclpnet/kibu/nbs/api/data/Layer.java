package work.lclpnet.kibu.nbs.api.data;

public interface Layer extends LayerInfo, NoteContainer {

    /**
     * @return The name of the layer.
     */
    String name();
}
