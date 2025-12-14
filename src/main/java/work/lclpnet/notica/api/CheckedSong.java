package work.lclpnet.notica.api;

import net.minecraft.resources.ResourceLocation;
import work.lclpnet.notica.api.data.Song;

import java.util.Random;

public record CheckedSong(Song song, ResourceLocation id, byte[] checksum) {

    public static CheckedSong ofRandomChecksum(Song song, ResourceLocation id, Random random) {
        byte[] checksum = new byte[8];
        random.nextBytes(checksum);

        return new CheckedSong(song, id, checksum);
    }
}
