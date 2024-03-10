package work.lclpnet.kibu.nbs.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.KibuNbsInit;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.network.SongSlicer;

public class RespondSongS2CPacket implements FabricPacket {

    public static final PacketType<RespondSongS2CPacket> TYPE =
            PacketType.create(KibuNbsInit.identifier("respond"), RespondSongS2CPacket::new);

    private final Identifier songId;
    private final SongSlice slice;
    private final boolean last;

    public RespondSongS2CPacket(Identifier songId, SongSlice slice, boolean last) {
        this.songId = songId;
        this.slice = slice;
        this.last = last;
    }

    public RespondSongS2CPacket(PacketByteBuf buf) {
        this.songId = buf.readIdentifier();
        this.last = buf.readBoolean();
        this.slice = SongSlicer.readSlice(buf);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(songId);
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

    public SongSlice getSlice() {
        return slice;
    }

    public boolean isLast() {
        return last;
    }
}
