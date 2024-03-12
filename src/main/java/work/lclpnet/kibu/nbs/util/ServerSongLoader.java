package work.lclpnet.kibu.nbs.util;

import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.api.CheckedSong;
import work.lclpnet.kibu.nbs.api.SongDecoder;
import work.lclpnet.kibu.nbs.api.data.Song;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class ServerSongLoader {

    public static CheckedSong load(InputStream input, Identifier id) throws IOException {
        CheckedInputStream in = new CheckedInputStream(input, new CRC32());

        Song song = SongDecoder.parse(in);

        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        buf.putLong(in.getChecksum().getValue());

        return new CheckedSong(song, id, buf.array());
    }
}
