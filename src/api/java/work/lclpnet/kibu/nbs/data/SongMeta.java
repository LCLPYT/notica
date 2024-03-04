package work.lclpnet.kibu.nbs.data;

public record SongMeta(String name, String author, String originalAuthor, String description) {

    public static final SongMeta EMPTY = new SongMeta("", "", "", "");
}
