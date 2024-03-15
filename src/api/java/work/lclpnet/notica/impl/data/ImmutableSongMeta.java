package work.lclpnet.notica.impl.data;

import work.lclpnet.notica.api.data.SongMeta;

public record ImmutableSongMeta(String name, String author, String originalAuthor, String description) implements SongMeta {
    public static final ImmutableSongMeta EMPTY = new ImmutableSongMeta("", "", "", "");
}
