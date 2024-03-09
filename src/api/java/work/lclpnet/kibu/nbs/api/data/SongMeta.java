package work.lclpnet.kibu.nbs.api.data;

public interface SongMeta {

    /**
     * @return The name of the song.
     */
    String name();

    /**
     * @return The author of this song.
     */
    String author();

    /**
     * @return The original author of this song.
     */
    String originalAuthor();

    /**
     * @return The song description.
     */
    String description();
}
