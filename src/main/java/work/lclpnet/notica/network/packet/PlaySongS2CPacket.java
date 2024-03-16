package work.lclpnet.notica.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.network.SongHeader;
import work.lclpnet.notica.network.SongSlicer;

public class PlaySongS2CPacket implements FabricPacket {

    public static final PacketType<PlaySongS2CPacket> TYPE =
            PacketType.create(NoticaInit.identifier("play"), PlaySongS2CPacket::new);

    private final Identifier songId;
    private final float volume;
    private final byte[] checksum;
    private final SongHeader header;
    private final boolean last;
    private final SongSlice slice;
    private final int startTick;

    public PlaySongS2CPacket(Identifier songId, float volume, int startTick, byte[] checksum, SongHeader header, boolean last, SongSlice slice) {
        this.songId = songId;
        this.volume = volume;
        this.checksum = checksum;
        this.header = header;
        this.last = last;
        this.slice = slice;
        this.startTick = startTick;
    }

    public PlaySongS2CPacket(PacketByteBuf buf) {
        this.songId = buf.readIdentifier();
        this.volume = buf.readFloat();
        this.startTick = buf.readInt();
        this.checksum = buf.readByteArray();
        this.header = new SongHeader(buf);
        this.last = buf.readBoolean();
        this.slice = SongSlicer.readSlice(buf);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(songId);
        buf.writeFloat(volume);
        buf.writeInt(startTick);
        buf.writeByteArray(checksum);
        header.write(buf);
        buf.writeBoolean(last);
        SongSlicer.writeSlice(buf, slice);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public Identifier getSongId() {
        return songId;
    }

    public float getVolume() {
        return volume;
    }

    public int getStartTick() {
        return startTick;
    }

    public byte[] getChecksum() {
        return checksum;
    }

    public SongHeader getHeader() {
        return header;
    }

    public SongSlice getSlice() {
        return slice;
    }

    public boolean isLast() {
        return last;
    }
}
