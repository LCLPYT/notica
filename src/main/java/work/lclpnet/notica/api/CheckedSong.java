package work.lclpnet.notica.api;

import net.minecraft.util.Identifier;
import work.lclpnet.notica.api.data.Song;

import java.util.Random;

public record CheckedSong(Song song, Identifier id, byte[] checksum) {

    public static CheckedSong ofRandomChecksum(Song song, Identifier id, Random random) {
        byte[] checksum = new byte[8];
        random.nextBytes(checksum);

        return new CheckedSong(song, id, checksum);
    }
}
