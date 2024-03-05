package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.Identifier;

/**
 * A song descriptor that can be used to identify a song.
 * @param id An arbitrary id that references a song between client and server.
 * @param checksum A checksum of the song file; the actual checksum algorithm can be any, e.g. crc32.
 */
public record SongDescriptor(Identifier id, byte[] checksum) {

}
