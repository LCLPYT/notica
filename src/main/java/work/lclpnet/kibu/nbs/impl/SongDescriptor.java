package work.lclpnet.kibu.nbs.impl;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * A song descriptor that can be used to identify a song.
 * @param id An arbitrary id that references a song between client and server.
 * @param checksum A checksum of the song file; the actual checksum algorithm can be any, e.g. crc32.
 */
public record SongDescriptor(Identifier id, byte[] checksum) {

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(id);
        buf.writeByteArray(checksum);
    }

    public static SongDescriptor read(PacketByteBuf buf) {
        Identifier id = buf.readIdentifier();
        byte[] checksum = buf.readByteArray();
        return new SongDescriptor(id, checksum);
    }
}
