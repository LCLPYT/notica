package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.SongMeta;

public record ImmutableSongMeta(String name, String author, String originalAuthor, String description) implements SongMeta {
    public static final ImmutableSongMeta EMPTY = new ImmutableSongMeta("", "", "", "");
}
